from fastapi import FastAPI
from pydantic import BaseModel
import random

app = FastAPI()

class EmbeddingRequest(BaseModel):
    model: str
    provider: str
    text: str

@app.post("/embed")
def embed(req: EmbeddingRequest):
    # Simulate a 1024-dim embedding vector
    return {"embedding": [random.uniform(-0.1, 0.1) for _ in range(1024)]}
