"""KiloWatt ingestion API.

Run from this folder:
    .venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
"""

import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator

from fastapi import FastAPI
from pydantic import BaseModel, Field

DB_PATH = Path(__file__).parent / "kilowatt.db"

app = FastAPI(title="KiloWatt ingestion API", version="0.1.0")


class ReadingIn(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    kwh: float = Field(ge=0)
    captured_at: datetime


@contextmanager
def database() -> Iterator[sqlite3.Connection]:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute(
        "CREATE TABLE IF NOT EXISTS readings ("
        " id TEXT PRIMARY KEY,"
        " kwh REAL NOT NULL,"
        " captured_at TEXT NOT NULL,"
        " received_at TEXT NOT NULL)"
    )
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/api/v1/readings", status_code=201)
def create_reading(reading: ReadingIn) -> dict:
    received_at = datetime.now(timezone.utc).isoformat()
    captured_at = reading.captured_at.isoformat()
    # The app may retry after a timeout, so an id that already exists is replaced, not duplicated
    with database() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO readings (id, kwh, captured_at, received_at) VALUES (?, ?, ?, ?)",
            (reading.id, reading.kwh, captured_at, received_at),
        )
    print(f"reading stored: id={reading.id} kwh={reading.kwh} captured_at={captured_at}")
    return {
        "id": reading.id,
        "kwh": reading.kwh,
        "captured_at": captured_at,
        "received_at": received_at,
        "status": "created",
    }


@app.get("/api/v1/readings")
def list_readings() -> list[dict]:
    with database() as conn:
        rows = conn.execute("SELECT * FROM readings ORDER BY captured_at DESC").fetchall()
    return [dict(row) for row in rows]
