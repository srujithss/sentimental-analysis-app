#!/usr/bin/env python3
"""
export_model.py
===============
What this script does, in four steps:

  1. DOWNLOAD + EXPORT  the Hugging Face model (PyTorch format) and convert it to ONNX format,
                        which is the format the Java app (DJL + ONNX Runtime) can load.
  2. SAVE the tokenizer (tokenizer.json) next to it. The tokenizer turns text into numbers.
  3. CHECK that the three files the Java app needs exist: model.onnx, tokenizer.json, config.json.
  4. VERIFY by running the 10 sample sentences through the exported model in Python and printing the
     predictions. Compare them with the Java startup log: they should match.

Usage (from the project's python/ folder, with the virtual environment active):

    python export_model.py                 # export + verify
    python export_model.py --skip-export   # only verify an already exported model
"""

# --------------------------------------------------------------------------------------
# 1. IMPORTS
#    "import x" loads a library so the names inside it can be used as x.something.
# --------------------------------------------------------------------------------------
from __future__ import annotations   # lets type hints like "list[str]" work on older Python 3 versions

import argparse        # standard library: reads command-line options such as --skip-export
import csv             # standard library: reads the tab-separated sample file
import json            # standard library: reads config.json
import re              # standard library: regular expressions (pattern-based text replacement)
import shutil          # standard library: shutil.which() finds an installed program on your PATH
import subprocess      # standard library: runs another program (here: optimum-cli) from Python
import sys             # standard library: sys.exit() stops the script with a message
import unicodedata     # standard library: Unicode normalization (NFC)
from pathlib import Path   # standard library: file paths that work the same on Mac/Windows/Linux

import numpy as np                       # third party: numeric arrays; "as np" is a short alias
import onnxruntime as ort                # third party: runs .onnx model files
from transformers import AutoTokenizer   # third party: loads the right tokenizer for a model


# --------------------------------------------------------------------------------------
# 2. SETTINGS (constants). By convention constants are written in UPPER_CASE.
# --------------------------------------------------------------------------------------
DEFAULT_MODEL_ID = "cardiffnlp/twitter-xlm-roberta-base-sentiment"   # name on huggingface.co
DEFAULT_OUT_DIR = Path("../models/xlmr-sentiment")                   # where the files are written
DEFAULT_SAMPLES = Path("../src/main/resources/samples/test-sentences.tsv")
MAX_LENGTH = 256   # longer texts are cut off; must match app.model.max-length in application.yml
REQUIRED_FILES = ["model.onnx", "tokenizer.json", "config.json"]


# --------------------------------------------------------------------------------------
# 3. TEXT CLEANING
#    Mirrors PreprocessingService.java exactly, so Python and Java feed the model the same text.
#    "def name(argument: type) -> return_type:" defines a function. The ": str" and "-> str"
#    are type hints: documentation for humans, Python does not enforce them.
# --------------------------------------------------------------------------------------
def preprocess(text: str) -> str:
    text = unicodedata.normalize("NFC", text)               # one canonical form for each letter
    text = text.replace("\u200b", "").replace("\ufeff", "")   # remove zero-width space and BOM
    text = re.sub(r"\s+", " ", text)                        # many spaces/tabs/newlines -> one space
    text = re.sub(r"@[A-Za-z0-9_]+", "@user", text)         # @mentions -> "@user"
    text = re.sub(r"https?://\S+|www\.\S+", "http", text)   # links -> "http"
    return text.strip()                                     # remove spaces at both ends


# --------------------------------------------------------------------------------------
# 4. EXPORT
# --------------------------------------------------------------------------------------
def export_to_onnx(model_id: str, out_dir: Path) -> None:
    """Runs:  optimum-cli export onnx --model <id> --task text-classification <out_dir>"""
    cli = shutil.which("optimum-cli")   # returns the full path of the program, or None if not found
    if cli is None:
        sys.exit(
            "optimum-cli was not found. Activate the virtual environment and run:\n"
            "    pip install -r requirements.txt\n"
            "(older Optimum versions need:  pip install 'optimum[exporters,onnxruntime]')"
        )

    out_dir.mkdir(parents=True, exist_ok=True)   # create the folder (and parents) if missing

    command = [cli, "export", "onnx",
               "--model", model_id,
               "--task", "text-classification",
               str(out_dir)]                     # a list: each item is one word of the command
    print("Running:", " ".join(command))
    # check=True means: if the command fails, raise an error instead of silently continuing.
    subprocess.run(command, check=True)


def save_tokenizer_and_config(model_id: str, out_dir: Path) -> None:
    """Makes sure tokenizer.json (and config.json) are in the output folder."""
    tokenizer = AutoTokenizer.from_pretrained(model_id)   # downloads the tokenizer (cached after 1st time)
    tokenizer.save_pretrained(out_dir)                    # writes tokenizer.json + related files

    if not (out_dir / "config.json").exists():            # "/" joins path parts for Path objects
        from transformers import AutoConfig               # imported here because it is rarely needed
        AutoConfig.from_pretrained(model_id).save_pretrained(out_dir)


def check_files(out_dir: Path) -> None:
    print(f"\nFiles in {out_dir.resolve()}:")
    missing = []                                          # an empty list we fill below
    for name in REQUIRED_FILES:                           # "for" repeats the block for each item
        path = out_dir / name
        if path.exists():
            size_mb = path.stat().st_size / (1024 * 1024)   # file size in bytes -> megabytes
            print(f"  OK       {name:<15} {size_mb:8.1f} MB")   # f-string: {value:format}
        else:
            print(f"  MISSING  {name}")
            missing.append(name)
    if missing:
        sys.exit(f"Export incomplete, missing: {', '.join(missing)}")


# --------------------------------------------------------------------------------------
# 5. VERIFY (run the exported model in Python)
# --------------------------------------------------------------------------------------
def load_labels(out_dir: Path) -> list[str]:
    """Reads id2label from config.json, e.g. {"0": "negative", "1": "neutral", "2": "positive"}.
    The ORDER matters: output number 0 of the model is the first label, and so on."""
    with open(out_dir / "config.json", encoding="utf-8") as f:   # "with" closes the file automatically
        config = json.load(f)                                    # JSON text -> Python dict
    id2label = config["id2label"]                                # dict with string keys "0","1","2"
    # sort numerically by the key, then keep only the label text (a list comprehension)
    return [label for _, label in sorted(id2label.items(), key=lambda item: int(item[0]))]


def softmax(logits: np.ndarray) -> np.ndarray:
    """Turns raw model scores (logits) into probabilities that add up to 1."""
    shifted = logits - np.max(logits)     # subtract the max for numerical stability (no overflow)
    exp = np.exp(shifted)                 # e^x for every score
    return exp / exp.sum()                # divide by the total so they sum to 1


def read_samples(path: Path) -> list[tuple[str, str]]:
    """Reads lines like  positive<TAB>some text  and returns [(expected, text), ...]."""
    samples = []
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.reader(f, delimiter="\t"):          # delimiter="\t" means tab-separated
            if not row or row[0].startswith("#"):          # skip blank lines and comment lines
                continue                                   # "continue" jumps to the next row
            samples.append((row[0].strip().lower(), row[1].strip()))
    return samples                                         # a list of (label, text) pairs (tuples)


def verify(out_dir: Path, samples_path: Path) -> None:
    labels = load_labels(out_dir)
    print(f"\nLabel order from config.json: {labels}")

    tokenizer = AutoTokenizer.from_pretrained(out_dir)     # load from the exported folder
    # An "InferenceSession" is a loaded ONNX model that can be called repeatedly.
    session = ort.InferenceSession(str(out_dir / "model.onnx"), providers=["CPUExecutionProvider"])
    input_names = {i.name for i in session.get_inputs()}   # a set, e.g. {"input_ids", "attention_mask"}
    print(f"ONNX model inputs: {sorted(input_names)}")

    samples = read_samples(samples_path)
    correct = 0
    print(f"\nPredictions for {len(samples)} sample sentences:")
    for number, (expected, text) in enumerate(samples, start=1):   # enumerate adds a counter
        cleaned = preprocess(text)
        # The tokenizer cuts the text into sub-word pieces and replaces each by a number (an id).
        # return_tensors="np" gives numpy arrays; truncation cuts text longer than max_length.
        encoded = tokenizer(cleaned, return_tensors="np", truncation=True, max_length=MAX_LENGTH)
        # Send only the inputs the ONNX model declares, as 64-bit integers.
        feed = {name: array.astype("int64") for name, array in encoded.items() if name in input_names}
        logits = session.run(None, feed)[0][0]             # [0] = first output, [0] = first sentence
        probabilities = softmax(logits)
        best = int(np.argmax(probabilities))               # index of the largest probability
        predicted = labels[best].lower()
        ok = predicted == expected
        correct += ok                                      # True counts as 1, False as 0
        print(f"{number:>2}. [{'OK  ' if ok else 'MISS'}] expected={expected:<8} "
              f"predicted={predicted:<8} conf={probabilities[best]:.2f} | {text}")

    print(f"\nPython accuracy: {correct}/{len(samples)}")
    print("Start the Spring Boot app and compare its startup log with the lines above.")


# --------------------------------------------------------------------------------------
# 6. MAIN: reads the command-line options and runs the steps in order
# --------------------------------------------------------------------------------------
def main() -> None:
    parser = argparse.ArgumentParser(description="Export a Hugging Face sentiment model to ONNX for the Java app.")
    parser.add_argument("--model-id", default=DEFAULT_MODEL_ID, help="model name on huggingface.co")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT_DIR, help="output folder")
    parser.add_argument("--samples", type=Path, default=DEFAULT_SAMPLES, help="tab-separated sample sentences")
    parser.add_argument("--skip-export", action="store_true", help="only verify an existing export")
    args = parser.parse_args()   # args.model_id, args.out, ... (dashes become underscores)

    if not args.skip_export:
        export_to_onnx(args.model_id, args.out)
        save_tokenizer_and_config(args.model_id, args.out)
    check_files(args.out)
    verify(args.out, args.samples)


# This line means: run main() only when the file is executed directly (python export_model.py),
# not when it is imported from another Python file.
if __name__ == "__main__":
    main()
