"""
Smart Notes AI Backend (RAG + Groq)
------------------------------------
- Semantic search (FAISS + embeddings)
- Retrieval-Augmented Generation (RAG)
- LLM via Groq API (fast + stable)
"""

from contextlib import asynccontextmanager
from datetime import datetime
from typing import Any, Optional

import faiss
import httpx
import json
import numpy as np
import os
from dotenv import load_dotenv
from fastapi import FastAPI
from groq import Groq
from pydantic import BaseModel, Field, AliasChoices
from sentence_transformers import SentenceTransformer

import firebase_admin
from firebase_admin import credentials, db

load_dotenv()

FIREBASE_CREDENTIALS_PATH = os.getenv(
    "FIREBASE_CREDENTIALS_PATH",
    "smart-note-75b48-firebase-adminsdk-fbsvc-93a7aac4a6.json",
)


def _derive_firebase_db_url() -> Optional[str]:
    configured_url = os.getenv("FIREBASE_DB_URL") or os.getenv("FIREBASE_DATABASE_URL")
    if configured_url:
        return configured_url.rstrip("/")

    if not os.path.exists(FIREBASE_CREDENTIALS_PATH):
        return None

    try:
        with open(FIREBASE_CREDENTIALS_PATH, "r", encoding="utf-8") as f:
            service_account = json.load(f)
        project_id = service_account.get("project_id")
        if project_id:
            return f"https://{project_id}-default-rtdb.firebaseio.com"
    except Exception:
        return None

    return None


FIREBASE_DB_URL = _derive_firebase_db_url()

if FIREBASE_DB_URL:
    if not os.path.exists(FIREBASE_CREDENTIALS_PATH):
        raise FileNotFoundError(
            f"Firebase credentials file not found: {FIREBASE_CREDENTIALS_PATH}"
        )

    cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)

    if not firebase_admin._apps:
        firebase_admin.initialize_app(
            cred,
            {
                "databaseURL": FIREBASE_DB_URL,
            },
        )

DATA_FILE = os.getenv("DATA_FILE", "notes_data.json")
FIREBASE_DATABASE_URL = (FIREBASE_DB_URL or "").rstrip("/")
FIREBASE_NOTES_PATH = os.getenv("FIREBASE_NOTES_PATH", "notes").strip("/")
FIREBASE_AUTH_TOKEN = os.getenv("FIREBASE_AUTH_TOKEN")
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GROQ_MODEL = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")

DIMENSION = 384
EMBEDDING_MODEL = "all-MiniLM-L6-v2"

model: Optional[SentenceTransformer] = None
client: Optional[Groq] = None
index: Optional[faiss.IndexFlatL2] = None
notes = []
embeddings_store = []
"""
getting notes from firebase
"""
def get_notes_from_firebase():
    if not FIREBASE_DB_URL:
        return []

    ref = db.reference(FIREBASE_NOTES_PATH or "notes")

    snapshot = ref.get()

    if not snapshot:
        return []

    firebase_notes = []

    if isinstance(snapshot, dict):
        items = snapshot.items()
    elif isinstance(snapshot, list):
        items = enumerate(snapshot)
    else:
        return []

    for note_id, note in items:
        if not isinstance(note, dict):
            continue

        text = note.get("text", "")
        if not text:
            title = note.get("title", "")
            content = note.get("content", "")
            text = f"{title}\n{content}".strip()

        if not text:
            continue

        firebase_notes.append({
            "id": note_id,
            "text": text,
            "created_at": note.get("created_at") or note.get("createdAt"),
        })

    return firebase_notes

def _firebase_notes_url() -> Optional[str]:
    if not FIREBASE_DATABASE_URL or not FIREBASE_NOTES_PATH:
        return None
    return f"{FIREBASE_DATABASE_URL}/{FIREBASE_NOTES_PATH}.json"


def _normalize_note_text(item: dict[str, Any]) -> str:
    text = item.get("text")
    if isinstance(text, str) and text.strip():
        return text.strip()

    title = item.get("title", "")
    content = item.get("content", "")
    parts = [str(part).strip() for part in (title, content) if str(part).strip()]
    return "\n".join(parts).strip()


def _normalize_notes_payload(payload: Any) -> list[dict[str, Any]]:
    if payload is None:
        return []

    if isinstance(payload, dict):
        note_like_keys = {"text", "content", "title", "embedding", "created_at", "createdAt"}
        if note_like_keys.intersection(payload.keys()):
            items = [payload]
        else:
            items = list(payload.values())
    elif isinstance(payload, list):
        items = payload
    else:
        return []

    normalized = []
    for fallback_id, item in enumerate(items, start=1):
        if not isinstance(item, dict):
            continue

        text = _normalize_note_text(item)
        if not text:
            continue

        normalized.append(
            {
                "id": item.get("id") or item.get("note_id") or fallback_id,
                "text": text,
                "created_at": item.get("created_at") or item.get("createdAt") or datetime.utcnow().isoformat(),
                "embedding": item.get("embedding"),
            }
        )

    return normalized


def _load_index_from_records(records: list[dict[str, Any]], source_label: str):
    global notes, embeddings_store, index

    notes = []
    embeddings_store = []
    index = faiss.IndexFlatL2(DIMENSION)
    seen_texts = set()

    print(f"📚 Chargement de {len(records)} notes depuis {source_label}...")

    for item in records:
        emb_data = item.get("embedding")
        if emb_data:
            emb = np.array(emb_data, dtype="float32")
        else:
            print(f"   Génération de l'embedding pour la note {item['id']}...")
            emb = encode(item["text"])

        normalized_text = item["text"].strip()
        if normalized_text in seen_texts:
            continue

        seen_texts.add(normalized_text)
        index.add(np.array([emb], dtype="float32"))
        embeddings_store.append(emb)
        notes.append(
            {
                "id": item["id"],
                "text": normalized_text,
                "created_at": item["created_at"],
            }
        )

    print(f"✅ Index FAISS créé avec {index.ntotal} vecteurs")


def _fetch_notes_from_firebase() -> Optional[list[dict[str, Any]]]:
    url = _firebase_notes_url()
    if not url:
        return None

    params = {}
    if FIREBASE_AUTH_TOKEN:
        params["auth"] = FIREBASE_AUTH_TOKEN

    try:
        response = httpx.get(url, params=params, timeout=10.0)
        response.raise_for_status()
        return _normalize_notes_payload(response.json())
    except Exception as exc:
        print(f"[WARN] Firebase fetch failed: {exc}")
        return None

"""
def load_data():
    firebase_records = _fetch_notes_from_firebase()
    if firebase_records is not None:
        _load_index_from_records(firebase_records, "Firebase Realtime Database")
        return

    global notes, embeddings_store, index

    notes = []
    embeddings_store = []
    index = faiss.IndexFlatL2(DIMENSION)

    if not os.path.exists(DATA_FILE):
        print(f"⚠️ Fichier {DATA_FILE} non trouvé, création d'un fichier par défaut...")
        create_default_data()
        return

    with open(DATA_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)

    _load_index_from_records(_normalize_notes_payload(data), f"fichier local {DATA_FILE}")

"""

def load_data():
    global notes
    global embeddings_store
    global index

    if FIREBASE_DB_URL:
        records = _fetch_notes_from_firebase()
        source_label = "Firebase Realtime Database (REST)"

        if records is None:
            records = get_notes_from_firebase()
            source_label = "Firebase Realtime Database (Admin SDK)"

        records = records or []
        _load_index_from_records(_normalize_notes_payload(records), source_label)
        return

    print("[WARN] Firebase DB URL not configured; using local notes_data.json fallback")

    if not os.path.exists(DATA_FILE):
        create_default_data()

    with open(DATA_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)

    _load_index_from_records(_normalize_notes_payload(data), f"fichier local {DATA_FILE}")


def create_default_data():
    default_notes = [
        {
            "id": 1,
            "text": "Bienvenue sur Smart Note - Prenez des notes intelligentes avec l'assistant IA. Cette application vous permet de créer, organiser et retrouver vos notes facilement.",
            "created_at": datetime.utcnow().isoformat(),
        },
        {
            "id": 2,
            "text": "Kotlin est un langage de programmation moderne pour le développement Android. Il est concis, sûr et interoperable avec Java.",
            "created_at": datetime.utcnow().isoformat(),
        },
        {
            "id": 3,
            "text": "L'architecture MVVM sépare l'interface utilisateur, la logique métier et les données. Elle utilise ViewModel et LiveData pour une meilleure organisation.",
            "created_at": datetime.utcnow().isoformat(),
        },
        {
            "id": 4,
            "text": "Retrofit est une bibliothèque pour les appels API en Android. Elle simplifie la communication avec les services web RESTful.",
            "created_at": datetime.utcnow().isoformat(),
        },
    ]

    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(default_notes, f, ensure_ascii=False, indent=2)

    print(f"✅ Fichier {DATA_FILE} créé avec {len(default_notes)} notes par défaut")


def save_data():
    data = []
    for i, note in enumerate(notes):
        data.append(
            {
                **note,
                "embedding": embeddings_store[i].tolist(),
            }
        )

    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def push_note_to_firebase(note: dict[str, Any]):
    url = _firebase_notes_url()
    if not url:
        return

    payload = {
        "id": note["id"],
        "text": note["text"],
        "created_at": note["created_at"],
    }

    params = {}
    if FIREBASE_AUTH_TOKEN:
        params["auth"] = FIREBASE_AUTH_TOKEN

    try:
        httpx.post(url, params=params, json=payload, timeout=10.0).raise_for_status()
    except Exception as exc:
        print(f"[WARN] Firebase push failed: {exc}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, client, index

    print("Loading embedding model...")
    model = SentenceTransformer(EMBEDDING_MODEL)
    index = faiss.IndexFlatL2(DIMENSION)

    print("Initializing Groq client...")
    if not GROQ_API_KEY:
        raise ValueError("GROQ_API_KEY environment variable is not set")

    client = Groq(api_key=GROQ_API_KEY)
    print(f"Using model: {GROQ_MODEL}")

    load_data()
    print("Backend ready 🚀")

    yield

    print("Shutdown")


app = FastAPI(title="Smart Notes AI", lifespan=lifespan)


class NoteCreate(BaseModel):
    text: str


class AskRequest(BaseModel):
    question: str = Field(
        validation_alias=AliasChoices("question", "prompt", "input", "q")
    )


class AskResponse(BaseModel):
    question: str
    answer: str


def encode(text: str):
    emb = model.encode([text])[0]
    return np.array(emb, dtype="float32")


def retrieve(query: str, k=3):
    if not notes:
        return []

    q = encode(query)
    distances, indices = index.search(np.array([q], dtype="float32"), k=min(k, len(notes)))

    results = []
    for i in indices[0]:
        if i < len(notes):
            results.append(notes[i]["text"])

    return results


@app.get("/")
def root():
    return {"status": "running", "notes": len(notes)}


@app.get("/health")
def health():
    return {"status": "ok", "notes": len(notes)}


@app.post("/notes")
def add_note(payload: NoteCreate):
    emb = encode(payload.text)

    index.add(np.array([emb], dtype="float32"))
    embeddings_store.append(emb)

    note = {
        "id": len(notes) + 1,
        "text": payload.text,
        "created_at": datetime.utcnow().isoformat(),
    }

    notes.append(note)
    save_data()
    push_note_to_firebase(note)
    return note


@app.post("/ask", response_model=AskResponse)
def ask(payload: AskRequest):
    if client is None:
        return AskResponse(
            question=payload.question,
            answer="Error: Groq client not initialized",
        )

   
    load_data()

    context_notes = retrieve(payload.question)
    context = "\n".join(context_notes) if context_notes else ""

    try:
        messages = [
            {
                "role": "system",
                "content": (
                    "You are Smart Notes AI. Answer clearly and concisely. "
                    "Use the provided note context when relevant. "
                    "If no context is relevant, still answer normally."
                ),
            },
            {
                "role": "user",
                "content": f"Context:\n{context}\n\nQuestion: {payload.question}\n\nAnswer:",
            },
        ]

        response = client.chat.completions.create(
            model=GROQ_MODEL,
            max_tokens=500,
            messages=messages,
            temperature=0.7,
        )

        answer = response.choices[0].message.content or "Error: Empty Groq response"
        print(f"[DEBUG] Groq response: {answer}")

    except Exception as e:
        print(f"[ERROR] Groq API error: {e}")
        answer = f"Error: {str(e)}"

    return AskResponse(
        question=payload.question,
        answer=answer,
    )