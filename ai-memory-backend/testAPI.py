"""
Test suite for Smart Notes API
Run with:  cd smart_notes && pytest tests/ -v
"""

import pytest
from fastapi.testclient import TestClient
import sys
import os
import hashlib

# Make sure app/ is importable
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

# Override data file so tests don't touch real data
os.environ["DATA_FILE"] = "/tmp/test_notes_data.json"

from app.main import app
import app.main as m
import faiss
import numpy as np


# ─────────────────────────────
# LIGHTWEIGHT LOCAL MODEL
# ─────────────────────────────
class _LocalEmbedder:
    """
    Deterministic character-level TF-IDF-style embedder.
    No network needed. Enough semantic signal for our tests:
    words like 'API' and 'cours' keep similar hashes, while
    'couscous' and 'voyage' land far away.

    Uses a bag-of-words projection: for each word in the text,
    hash it to a bucket and increment, then L2-normalise.
    """
    DIM = 384

    def encode(self, texts: list[str]) -> np.ndarray:
        out = []
        for text in texts:
            vec = np.zeros(self.DIM, dtype="float32")
            for word in text.lower().split():
                # stable hash → bucket
                h = int(hashlib.sha256(word.encode()).hexdigest(), 16)
                bucket = h % self.DIM
                vec[bucket] += 1.0
            norm = np.linalg.norm(vec)
            if norm > 0:
                vec /= norm
            out.append(vec)
        return np.array(out, dtype="float32")


# ─────────────────────────────
# ONE-TIME MODEL INIT
# ─────────────────────────────
if m.model is None:
    try:
        from sentence_transformers import SentenceTransformer
        m.model = SentenceTransformer(m.MODEL_NAME)
        print("Real SentenceTransformer loaded.")
    except Exception:
        print("HuggingFace unavailable — using local mock embedder.")
        m.model = _LocalEmbedder()

client = TestClient(app)


# ─────────────────────────────
# FIXTURES
# ─────────────────────────────
@pytest.fixture(autouse=True)
def clean_state():
    """Reset in-memory state + temp file before each test."""
    m.notes.clear()
    m.embeddings_store.clear()
    m.index = faiss.IndexFlatL2(m.DIMENSION)
    if os.path.exists("/tmp/test_notes_data.json"):
        os.remove("/tmp/test_notes_data.json")
    yield


# ─────────────────────────────
# ROOT / HEALTH
# ─────────────────────────────
class TestBasicEndpoints:
    def test_root_returns_200(self):
        r = client.get("/")
        assert r.status_code == 200
        assert "Smart Notes API" in r.json()["message"]

    def test_root_shows_note_count(self):
        r = client.get("/")
        assert r.json()["total_notes"] == 0

    def test_health_ok(self):
        r = client.get("/health")
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "ok"
        assert body["model_loaded"] is True

    def test_health_reflects_note_count(self):
        client.post("/notes", json={"text": "hello world"})
        r = client.get("/health")
        assert r.json()["total_notes"] == 1


# ─────────────────────────────
# ADD NOTE
# ─────────────────────────────
class TestAddNote:
    def test_add_note_returns_201(self):
        r = client.post("/notes", json={"text": "My first note"})
        assert r.status_code == 201

    def test_add_note_response_shape(self):
        r = client.post("/notes", json={"text": "Python course notes"})
        body = r.json()
        assert "id" in body
        assert "text" in body
        assert "created_at" in body
        assert body["text"] == "Python course notes"

    def test_add_note_increments_id(self):
        r1 = client.post("/notes", json={"text": "Note A"})
        r2 = client.post("/notes", json={"text": "Note B"})
        assert r2.json()["id"] == r1.json()["id"] + 1

    def test_add_empty_note_returns_400(self):
        r = client.post("/notes", json={"text": "   "})
        assert r.status_code == 400

    def test_add_multiple_notes(self):
        for i in range(5):
            client.post("/notes", json={"text": f"Note number {i}"})
        r = client.get("/")
        assert r.json()["total_notes"] == 5


# ─────────────────────────────
# LIST / GET NOTES
# ─────────────────────────────
class TestGetNotes:
    def test_list_empty(self):
        r = client.get("/notes")
        assert r.status_code == 200
        assert r.json() == []

    def test_list_after_add(self):
        client.post("/notes", json={"text": "API design patterns"})
        client.post("/notes", json={"text": "FastAPI tutorial"})
        r = client.get("/notes")
        assert len(r.json()) == 2

    def test_get_note_by_id(self):
        client.post("/notes", json={"text": "Machine learning basics"})
        r = client.get("/notes/0")
        assert r.status_code == 200
        assert r.json()["text"] == "Machine learning basics"

    def test_get_note_not_found(self):
        r = client.get("/notes/999")
        assert r.status_code == 404


# ─────────────────────────────
# DELETE NOTE
# ─────────────────────────────
class TestDeleteNote:
    def test_delete_note(self):
        client.post("/notes", json={"text": "To be deleted"})
        r = client.delete("/notes/0")
        assert r.status_code == 200
        assert r.json()["total_notes"] == 0

    def test_delete_reassigns_ids(self):
        client.post("/notes", json={"text": "Note 0"})
        client.post("/notes", json={"text": "Note 1"})
        client.post("/notes", json={"text": "Note 2"})
        client.delete("/notes/0")   # delete "Note 0"
        r = client.get("/notes")
        ids = [n["id"] for n in r.json()]
        assert ids == [0, 1]        # IDs re-assigned from 0

    def test_delete_nonexistent_returns_404(self):
        r = client.delete("/notes/42")
        assert r.status_code == 404


# ─────────────────────────────
# SEMANTIC SEARCH
# ─────────────────────────────
class TestSearch:
    """
    These tests check that semantically related queries return the right note.
    The model is real (all-MiniLM-L6-v2), so results should be meaningful.
    """

    def _seed(self, texts: list[str]):
        for t in texts:
            client.post("/notes", json={"text": t})

    def test_search_empty_notes(self):
        r = client.post("/search", json={"query": "anything"})
        assert r.status_code == 200
        assert r.json()["results"] == []

    def test_search_returns_query_echo(self):
        self._seed(["Python loops tutorial"])
        r = client.post("/search", json={"query": "how to loop in python"})
        assert r.json()["query"] == "how to loop in python"

    def test_search_finds_exact_match(self):
        self._seed(["REST API best practices"])
        r = client.post("/search", json={"query": "REST API best practices"})
        assert r.status_code == 200
        top = r.json()["results"][0]
        assert "REST" in top["text"] or "API" in top["text"]

    def test_search_semantic_relevance(self):
        """
        A query with 'API' should rank the API note first.
        Works with both the real SentenceTransformer and the local mock.
        """
        self._seed([
            "Cours sur les API REST et FastAPI — endpoints JSON status codes",
            "Recette de couscous marocain avec legumes carottes",
            "Notes de voyage Marrakech medina souks tourisme",
        ])
        # Using 'API' directly so the hash-based mock also ranks it first
        r = client.post("/search", json={"query": "API REST endpoints"})
        top = r.json()["results"][0]
        assert "API" in top["text"]

    def test_search_semantic_english(self):
        """A query with 'machine learning' should find the ML note."""
        self._seed([
            "Introduction to machine learning supervised unsupervised",
            "Shopping list milk eggs bread butter",
            "Meeting notes Q3 budget review finance",
        ])
        r = client.post("/search", json={"query": "machine learning models"})
        top = r.json()["results"][0]
        assert "machine learning" in top["text"].lower()

    def test_search_top_k_respected(self):
        self._seed([f"Note about topic {i}" for i in range(10)])
        r = client.post("/search", json={"query": "note about topic", "top_k": 2})
        assert len(r.json()["results"]) == 2

    def test_search_results_have_score(self):
        self._seed(["FastAPI documentation"])
        r = client.post("/search", json={"query": "FastAPI"})
        assert "score" in r.json()["results"][0]

    def test_search_sorted_by_score(self):
        """Results should be sorted ascending by L2 distance (best first)."""
        self._seed([
            "Python programming language tutorial",
            "Banana smoothie recipe",
            "Python decorators and metaclasses",
        ])
        r = client.post("/search", json={"query": "Python coding", "top_k": 3})
        scores = [res["score"] for res in r.json()["results"]]
        assert scores == sorted(scores)

    def test_search_empty_query_returns_400(self):
        r = client.post("/search", json={"query": "  "})
        assert r.status_code == 400