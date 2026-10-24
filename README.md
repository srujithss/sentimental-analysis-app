# Cross-Lingual Sentiment Analysis (Spring Boot + DJL)

## Phase 1: auth, roles, two dashboards (already running on your machine)

- Java 21, Spring Boot 3.5.x, Spring Security 6, Thymeleaf, JPA, H2 (file DB in ./data)
- USER area:  /user/**   (users operate)
- ADMIN area: /admin/**  (admin monitors)
- Public registration always creates a USER. The admin is seeded on first start.

## Run

1. Open the folder in IntelliJ (File > Open > pom.xml > Open as Project). Let Maven import finish.
2. Set the JDK to 21 (File > Project Structure > Project SDK).
3. Run `SentimentApplication`.
4. Open http://localhost:8080

Admin login (change before sharing): `admin` / `Admin@12345`
Override with the environment variable `ADMIN_PASSWORD` (and edit `app.admin.*` in application.yml).

## Tests

`mvn test` runs SecurityAccessTest (role access rules).

## Next phases

2. DJL + ONNX model loading and a console check
3. User: analyze, history, feedback
4. Admin: users, predictions, stats charts
5. Admin: gold set + evaluation, audit log

---

## Phase 2: model loading with DJL + ONNX (this download)

New: DJL dependencies, `ModelProperties`, `PreprocessingService`, `LanguageDetectionService`
(script based), `DjlSentimentService`, startup check, admin "Sentiment model" tile, Python export script.

### 1. Export the model (once)

```bash
cd python
python3 -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install --upgrade pip
pip install -r requirements.txt
python export_model.py
```

This creates `models/xlmr-sentiment/` (model.onnx, tokenizer.json, config.json) in the project root and
prints the Python predictions for the 10 sample sentences. The first run downloads about 1 GB.

### 2. Run the app

Run `SentimentApplication`. The console shows the startup check: one line per sentence plus accuracy
and a confusion matrix. Compare it with the Python output.

If the model folder is missing the app still starts and the admin dashboard shows "Not loaded".

### Model

`cardiffnlp/twitter-xlm-roberta-base-sentiment` (3 labels: negative, neutral, positive). It was fine-tuned
for sentiment on 8 languages that do NOT include Tamil, so this is the zero-shot cross-lingual BASELINE.
Expect weaker results on Tamil, and weakest on romanized text. A later phase fine-tunes a model on
Dravidian data and compares against this baseline.

### Tests

`mvn test`. DjlSentimentServiceTest is skipped automatically until the model has been exported.

---

## Phase 3: user interface for analysis (this download)

- `/user/analyze`: text box, example buttons, result card (label, confidence, per-label bars, language/script,
  code-mixed and low-confidence warnings). Ctrl/Cmd + Enter also submits.
- `/user/history`: your own past analyses, paged. Every record belongs to the logged-in user.
- `POST /api/sentiment` and `GET /api/sentiment/history` (USER role only; the username comes from the login,
  never from the request).
- If the model is not loaded the page shows a warning and the API answers 503 with a readable message.

Not built yet: label correction (feedback), admin views of all predictions, evaluation.

---

## Fine-tuning for romanized / code-mixed text

The baseline model handles romanized Dravidian text ("padam semma mass") poorly. `python/finetune.py`
fine-tunes `google/muril-base-cased` (pre-trained on Indian languages including romanized text) on the
DravidianCodeMix dataset (Tamil-English, Malayalam-English, Kannada-English YouTube comments; downloaded
from Zenodo record 4750858 by the script).

Run it on Google Colab with a T4 GPU (about 30-60 minutes):

```
!pip install -q transformers accelerate scikit-learn onnx onnxruntime
!python finetune.py              # upload finetune.py first; see the options at the top of the file
from google.colab import files
files.download("muril-sentiment.zip")
```

Then locally:

```bash
unzip muril-sentiment.zip -d models/
cd python && python export_model.py --skip-export --out ../models/muril-sentiment
```

and set `app.model.path: ./models/muril-sentiment` in application.yml. The script prints test scores
overall, per language and for romanized vs native script (also saved as `training_report.json`).
Labels: Positive -> positive, Negative -> negative, unknown_state -> neutral; Mixed_feelings and
not-<language> comments are dropped (`--mixed neutral` keeps Mixed_feelings as neutral).
There is no Telugu data in this dataset.
