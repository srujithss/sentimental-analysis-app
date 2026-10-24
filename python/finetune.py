#!/usr/bin/env python3
"""
finetune.py
===========
Fine-tunes a sentiment model on code-mixed / romanized Dravidian text and exports it in the
format the Java app loads (model.onnx + tokenizer.json + config.json).

What this script does, in five steps:

  1. DOWNLOAD the DravidianCodeMix dataset (Chakravarthi et al., Zenodo record 4750858):
     YouTube comments in Tamil-English, Malayalam-English and Kannada-English, mostly romanized.
  2. PREPARE the data: map the dataset labels to the app's three labels, clean the text with the
     SAME rules as PreprocessingService.java.
  3. TRAIN google/muril-base-cased (pre-trained on Indian languages INCLUDING romanized text).
  4. EVALUATE on the official test split: overall, per language, and romanized vs native script.
  5. EXPORT to ONNX with only two inputs (input_ids, attention_mask), which is what
     SentimentTranslator.java sends, and zip the folder for download.

Run it on Google Colab (Runtime > Change runtime type > T4 GPU), in a notebook cell:

    !pip install -q transformers accelerate scikit-learn onnx onnxruntime
    # upload this file with the Files panel on the left, then:
    !python finetune.py
    from google.colab import files
    files.download("muril-sentiment.zip")

Training takes roughly 30-60 minutes on a T4. Then, on your machine:

    unzip muril-sentiment.zip -d models/        # creates models/muril-sentiment/
    cd python && python export_model.py --skip-export --out ../models/muril-sentiment
    # set app.model.path: ./models/muril-sentiment in application.yml and restart the app

Options (all optional):
    --languages tamil,malayalam      train on a subset of languages
    --mixed neutral                  keep "Mixed_feelings" comments, labelled neutral (default: drop them)
    --epochs 4 --lr 3e-5             training settings
    --quick                          tiny run on 1% of the data, to check the pipeline works end to end
"""

from __future__ import annotations

import argparse
import hashlib
import inspect
import json
import random
import re
import shutil
import unicodedata
import urllib.request
import zipfile
from collections import Counter
from pathlib import Path

import numpy as np
import torch
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from transformers import (AutoModelForSequenceClassification, AutoTokenizer, DataCollatorWithPadding,
                          Trainer, TrainingArguments)


# --------------------------------------------------------------------------------------
# 1. SETTINGS
# --------------------------------------------------------------------------------------
DATA_URL = "https://zenodo.org/records/4750858/files/DravidianCodeMix-2020.zip?download=1"
DATA_MD5 = "7850be52919a387f5b36c7a09b05ad87"

# File names inside the zip, per language and split.
FILES = {
    "tamil":     {s: f"tamil_sentiment_full_{s}.csv" for s in ("train", "dev", "test")},
    "malayalam": {s: f"mal_full_sentiment_{s}.csv" for s in ("train", "dev", "test")},
    "kannada":   {s: f"kannada_sentiment_{s}.csv" for s in ("train", "dev", "test")},
}

# The app only understands these three labels, in this order (DjlSentimentService checks them).
LABELS = ["negative", "neutral", "positive"]

# Dataset label -> app label. "unknown_state" means the comment carries no sentiment, which is what
# "neutral" means in the app. "not-Tamil" etc. are comments in another language: dropped.
# "Mixed_feelings" is handled by the --mixed option.
LABEL_MAP = {"Positive": "positive", "Negative": "negative", "unknown_state": "neutral"}

DEFAULT_MODEL_ID = "google/muril-base-cased"
TRAIN_MAX_LENGTH = 128   # comments are short; 128 tokens covers almost all of them and trains faster
SEED = 42


# --------------------------------------------------------------------------------------
# 2. TEXT CLEANING: mirrors PreprocessingService.java and export_model.py exactly.
#    The model must see the same kind of text in training as it will get in the app.
# --------------------------------------------------------------------------------------
def preprocess(text: str) -> str:
    text = unicodedata.normalize("NFC", text)
    text = text.replace("​", "").replace("﻿", "")
    text = re.sub(r"\s+", " ", text)
    text = re.sub(r"@[A-Za-z0-9_]+", "@user", text)
    text = re.sub(r"https?://\S+|www\.\S+", "http", text)
    return text.strip()


def is_romanized(text: str) -> bool:
    """True when most letters are Latin, i.e. romanized Dravidian (or English)."""
    letters = [c for c in text if c.isalpha()]
    if not letters:
        return False
    latin = sum(1 for c in letters if c.isascii())
    return latin / len(letters) > 0.5


# --------------------------------------------------------------------------------------
# 3. DATA
# --------------------------------------------------------------------------------------
def download_dataset(work_dir: Path) -> Path:
    """Downloads and unpacks the zip once; returns the folder with the CSV files."""
    work_dir.mkdir(parents=True, exist_ok=True)
    zip_path = work_dir / "DravidianCodeMix-2020.zip"
    if not zip_path.exists():
        print(f"Downloading {DATA_URL}")
        urllib.request.urlretrieve(DATA_URL, zip_path)
    md5 = hashlib.md5(zip_path.read_bytes()).hexdigest()
    if md5 != DATA_MD5:
        zip_path.unlink()
        raise SystemExit(f"Downloaded file is corrupt (md5 {md5}); run the script again.")
    data_dir = work_dir / "DravidianCodeMix"
    if not data_dir.exists():
        with zipfile.ZipFile(zip_path) as z:
            z.extractall(work_dir)
    return data_dir


def parse_line(line: str) -> tuple[str, str]:
    """The files mix two formats: 'text<TAB>label' and 'text;label;'. The label is always last,
    and the text itself may contain ';' or tabs, so split from the right."""
    line = line.rstrip("\r\n")
    if "\t" in line:
        text, _, label = line.rpartition("\t")
    else:
        if line.endswith(";"):
            line = line[:-1]
        text, _, label = line.rpartition(";")
    return text.strip(), label.strip()


def load_split(data_dir: Path, languages: list[str], split: str, mixed: str) -> list[dict]:
    """Returns [{"text", "label" (0..2), "language", "romanized"}, ...] for one split."""
    label_map = dict(LABEL_MAP)
    if mixed == "neutral":
        label_map["Mixed_feelings"] = "neutral"

    rows = []
    for language in languages:
        dropped = Counter()
        with open(data_dir / FILES[language][split], encoding="utf-8") as f:
            for line in f:
                raw_text, raw_label = parse_line(line)
                text = preprocess(raw_text)
                if raw_label not in label_map or not text:
                    dropped[raw_label or "<empty>"] += 1
                    continue
                rows.append({"text": text,
                             "label": LABELS.index(label_map[raw_label]),
                             "language": language,
                             "romanized": is_romanized(text)})
        print(f"  {split:<5} {language:<10} dropped: {dict(dropped)}")
    return rows


class EncodedDataset(torch.utils.data.Dataset):
    """Tokenized rows, in the form the Hugging Face Trainer expects."""

    def __init__(self, rows: list[dict], tokenizer):
        self.encodings = tokenizer([r["text"] for r in rows], truncation=True, max_length=TRAIN_MAX_LENGTH)
        self.labels = [r["label"] for r in rows]

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, i):
        item = {key: values[i] for key, values in self.encodings.items()}
        item["labels"] = self.labels[i]
        return item


# --------------------------------------------------------------------------------------
# 4. TRAINING
# --------------------------------------------------------------------------------------
class WeightedTrainer(Trainer):
    """The data is ~55% positive. Weighting the loss by inverse class frequency stops the model
    from simply predicting "positive" for anything it is unsure about."""

    def __init__(self, class_weights: torch.Tensor, **kwargs):
        super().__init__(**kwargs)
        self.class_weights = class_weights

    def compute_loss(self, model, inputs, return_outputs=False, **kwargs):
        labels = inputs.pop("labels")
        outputs = model(**inputs)
        loss_fn = torch.nn.CrossEntropyLoss(weight=self.class_weights.to(outputs.logits.device))
        loss = loss_fn(outputs.logits, labels)
        return (loss, outputs) if return_outputs else loss


def compute_metrics(eval_pred) -> dict:
    logits, labels = eval_pred
    predictions = np.argmax(logits, axis=-1)
    return {"accuracy": accuracy_score(labels, predictions),
            "macro_f1": f1_score(labels, predictions, average="macro"),
            "weighted_f1": f1_score(labels, predictions, average="weighted")}


def training_arguments(args, out_dir: Path) -> TrainingArguments:
    options = dict(output_dir=str(out_dir),
                   num_train_epochs=args.epochs,
                   learning_rate=args.lr,
                   per_device_train_batch_size=args.batch_size,
                   per_device_eval_batch_size=args.batch_size * 2,
                   warmup_ratio=0.1,
                   weight_decay=0.01,
                   save_strategy="epoch",
                   save_total_limit=1,
                   load_best_model_at_end=True,
                   metric_for_best_model="macro_f1",
                   fp16=torch.cuda.is_available(),
                   logging_steps=50,
                   report_to="none",
                   seed=SEED)
    # The argument was renamed in newer transformers versions.
    if "eval_strategy" in inspect.signature(TrainingArguments.__init__).parameters:
        options["eval_strategy"] = "epoch"
    else:
        options["evaluation_strategy"] = "epoch"
    return TrainingArguments(**options)


# --------------------------------------------------------------------------------------
# 5. EVALUATION
# --------------------------------------------------------------------------------------
def evaluate_test(trainer: Trainer, test_rows: list[dict], test_set: EncodedDataset) -> dict:
    predictions = np.argmax(trainer.predict(test_set).predictions, axis=-1)
    labels = np.array([r["label"] for r in test_rows])

    print("\n=== Test set ===")
    print(classification_report(labels, predictions, target_names=LABELS, digits=3, zero_division=0))
    print("Confusion matrix (rows = true, columns = predicted):", LABELS)
    print(confusion_matrix(labels, predictions, labels=list(range(len(LABELS)))))

    report = {"overall": subset_scores(labels, predictions)}
    groups = {f"language={lang}": [r["language"] == lang for r in test_rows]
              for lang in sorted({r["language"] for r in test_rows})}
    groups["script=romanized"] = [r["romanized"] for r in test_rows]
    groups["script=native"] = [not r["romanized"] for r in test_rows]

    print(f"\n{'subset':<22}{'rows':>6}{'accuracy':>10}{'macro F1':>10}{'weighted F1':>13}")
    for name, mask in [("overall", [True] * len(test_rows))] + list(groups.items()):
        mask = np.array(mask)
        if not mask.any():
            continue
        scores = subset_scores(labels[mask], predictions[mask])
        report[name] = scores
        print(f"{name:<22}{scores['rows']:>6}{scores['accuracy']:>10.3f}"
              f"{scores['macro_f1']:>10.3f}{scores['weighted_f1']:>13.3f}")
    return report


def subset_scores(labels, predictions) -> dict:
    return {"rows": int(len(labels)),
            "accuracy": float(accuracy_score(labels, predictions)),
            "macro_f1": float(f1_score(labels, predictions, average="macro", zero_division=0)),
            "weighted_f1": float(f1_score(labels, predictions, average="weighted", zero_division=0))}


# --------------------------------------------------------------------------------------
# 6. ONNX EXPORT
# --------------------------------------------------------------------------------------
class TwoInputModel(torch.nn.Module):
    """BERT-style models (like MuRIL) also accept token_type_ids, and a standard export would make
    it a required ONNX input. SentimentTranslator.java sends only input_ids and attention_mask,
    so the exported graph takes exactly those two (token_type_ids default to zeros inside)."""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask):
        return self.model(input_ids=input_ids, attention_mask=attention_mask).logits


def export_onnx(model_dir: Path, out_dir: Path, sample_texts: list[str]) -> None:
    print(f"\nExporting ONNX model to {out_dir}")
    out_dir.mkdir(parents=True, exist_ok=True)

    # "eager" attention exports cleanly; the optimized kernels used during training may not.
    model = AutoModelForSequenceClassification.from_pretrained(model_dir, attn_implementation="eager").eval()
    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    # eval() matters: export restores the wrapper's mode afterwards, and a wrapper left in training
    # mode would switch the model's dropout back on for the comparison below.
    wrapper = TwoInputModel(model).eval()

    dummy = tokenizer(sample_texts[0], return_tensors="pt")
    export_options = dict(input_names=["input_ids", "attention_mask"],
                          output_names=["logits"],
                          dynamic_axes={"input_ids": {0: "batch", 1: "sequence"},
                                        "attention_mask": {0: "batch", 1: "sequence"},
                                        "logits": {0: "batch"}},
                          opset_version=17)
    # Newer PyTorch versions default to a different exporter; ask for the classic one explicitly.
    if "dynamo" in inspect.signature(torch.onnx.export).parameters:
        export_options["dynamo"] = False
    with torch.no_grad():
        torch.onnx.export(wrapper, (dummy["input_ids"], dummy["attention_mask"]),
                          str(out_dir / "model.onnx"), **export_options)

    # tokenizer.json is what DJL's HuggingFaceTokenizer reads; config.json holds id2label.
    tokenizer.save_pretrained(out_dir)
    model.config.save_pretrained(out_dir)
    if not (out_dir / "tokenizer.json").exists():
        raise SystemExit("tokenizer.json was not written; the model needs a fast tokenizer.")

    check_onnx_matches_pytorch(model, tokenizer, out_dir / "model.onnx", sample_texts)


def check_onnx_matches_pytorch(model, tokenizer, onnx_path: Path, texts: list[str]) -> None:
    import onnxruntime as ort

    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    inputs = sorted(i.name for i in session.get_inputs())
    if inputs != ["attention_mask", "input_ids"]:
        raise SystemExit(f"Unexpected ONNX inputs {inputs}; the Java app sends input_ids and attention_mask.")

    worst = 0.0
    for text in texts:
        encoded = tokenizer(text, return_tensors="np", truncation=True, max_length=256)
        onnx_logits = session.run(None, {"input_ids": encoded["input_ids"].astype("int64"),
                                         "attention_mask": encoded["attention_mask"].astype("int64")})[0]
        with torch.no_grad():
            torch_logits = model(input_ids=torch.from_numpy(encoded["input_ids"]),
                                 attention_mask=torch.from_numpy(encoded["attention_mask"])).logits.numpy()
        worst = max(worst, float(np.abs(onnx_logits - torch_logits).max()))
    print(f"ONNX vs PyTorch on {len(texts)} test sentences: largest logit difference {worst:.2e}")
    if worst > 1e-3:
        raise SystemExit("The ONNX model does not match the PyTorch model; do not use this export.")


# --------------------------------------------------------------------------------------
# 7. MAIN
# --------------------------------------------------------------------------------------
def main() -> None:
    parser = argparse.ArgumentParser(description="Fine-tune a Dravidian code-mixed sentiment model.")
    parser.add_argument("--model-id", default=DEFAULT_MODEL_ID, help="base model on huggingface.co")
    parser.add_argument("--languages", default="tamil,malayalam,kannada",
                        help="comma-separated subset of: " + ", ".join(FILES))
    parser.add_argument("--mixed", choices=["drop", "neutral"], default="drop",
                        help='what to do with "Mixed_feelings" comments')
    parser.add_argument("--epochs", type=float, default=3)
    parser.add_argument("--lr", type=float, default=2e-5)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--work", type=Path, default=Path("work"), help="dataset and checkpoint folder")
    parser.add_argument("--out", type=Path, default=Path("muril-sentiment"), help="export folder for the app")
    parser.add_argument("--quick", action="store_true", help="1%% of the data, 1 epoch: pipeline check only")
    args = parser.parse_args()

    languages = [lang.strip() for lang in args.languages.split(",")]
    unknown = [lang for lang in languages if lang not in FILES]
    if unknown:
        raise SystemExit(f"Unknown language(s) {unknown}; choose from {list(FILES)}")

    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    print(f"Device: {'GPU ' + torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU (slow!)'}")

    # Data
    data_dir = download_dataset(args.work)
    print("Loading data:")
    train_rows, dev_rows, test_rows = (load_split(data_dir, languages, s, args.mixed)
                                       for s in ("train", "dev", "test"))
    if args.quick:
        train_rows, dev_rows, test_rows = (random.sample(r, max(30, len(r) // 100))
                                           for r in (train_rows, dev_rows, test_rows))
        args.epochs = 1
    counts = Counter(r["label"] for r in train_rows)
    print(f"Rows: train {len(train_rows)}, dev {len(dev_rows)}, test {len(test_rows)}")
    print("Train labels:", {LABELS[k]: v for k, v in sorted(counts.items())},
          f"| romanized share: {sum(r['romanized'] for r in train_rows) / len(train_rows):.0%}")

    # Model
    tokenizer = AutoTokenizer.from_pretrained(args.model_id)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model_id, num_labels=len(LABELS),
        id2label=dict(enumerate(LABELS)), label2id={label: i for i, label in enumerate(LABELS)})

    weights = torch.tensor([len(train_rows) / (len(LABELS) * counts.get(i, 1)) for i in range(len(LABELS))],
                           dtype=torch.float)
    print("Class weights:", dict(zip(LABELS, weights.round(decimals=2).tolist())))

    test_set = EncodedDataset(test_rows, tokenizer)
    trainer = WeightedTrainer(class_weights=weights,
                              model=model,
                              args=training_arguments(args, args.work / "checkpoints"),
                              train_dataset=EncodedDataset(train_rows, tokenizer),
                              eval_dataset=EncodedDataset(dev_rows, tokenizer),
                              data_collator=DataCollatorWithPadding(tokenizer),
                              compute_metrics=compute_metrics)
    trainer.train()

    report = evaluate_test(trainer, test_rows, test_set)

    # Save the best model, then export it for the Java app
    best_dir = args.work / "best-model"
    trainer.save_model(str(best_dir))
    tokenizer.save_pretrained(best_dir)

    if args.out.exists():
        shutil.rmtree(args.out)
    export_onnx(best_dir, args.out, [r["text"] for r in test_rows[:20]])

    report["settings"] = {"base_model": args.model_id, "languages": languages, "mixed": args.mixed,
                          "epochs": args.epochs, "lr": args.lr, "batch_size": args.batch_size,
                          "quick": args.quick}
    (args.out / "training_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

    zip_path = shutil.make_archive(str(args.out), "zip", root_dir=args.out.parent, base_dir=args.out.name)
    print(f"\nDone. Download {zip_path} and unzip it into the project's models/ folder.")


if __name__ == "__main__":
    main()
