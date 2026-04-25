from fastapi import FastAPI, APIRouter, HTTPException
from fastapi.responses import JSONResponse
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import logging
import uuid
import base64
import httpx
from pathlib import Path
from pydantic import BaseModel, Field, ConfigDict
from typing import List, Optional
from datetime import datetime, timezone

from emergentintegrations.llm.chat import LlmChat, UserMessage

ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / '.env')

mongo_url = os.environ['MONGO_URL']
client = AsyncIOMotorClient(mongo_url)
db = client[os.environ['DB_NAME']]

EMERGENT_LLM_KEY = os.environ.get('EMERGENT_LLM_KEY', '')

app = FastAPI(title="AioWeb Native Backend")
api_router = APIRouter(prefix="/api")


# ------- Status (kept) -------
class StatusCheck(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    client_name: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class StatusCheckCreate(BaseModel):
    client_name: str


@api_router.get("/")
async def root():
    return {"message": "AioWeb Native Backend", "version": "1.0.0"}


@api_router.post("/status", response_model=StatusCheck)
async def create_status_check(input: StatusCheckCreate):
    status_dict = input.model_dump()
    status_obj = StatusCheck(**status_dict)
    doc = status_obj.model_dump()
    doc['timestamp'] = doc['timestamp'].isoformat()
    _ = await db.status_checks.insert_one(doc)
    return status_obj


@api_router.get("/status", response_model=List[StatusCheck])
async def get_status_checks():
    status_checks = await db.status_checks.find({}, {"_id": 0}).to_list(1000)
    for c in status_checks:
        if isinstance(c['timestamp'], str):
            c['timestamp'] = datetime.fromisoformat(c['timestamp'])
    return status_checks


# ------- AI: Chat (text) -------
class ChatRequest(BaseModel):
    message: str
    session_id: Optional[str] = None
    provider: str = "openai"   # openai | anthropic | gemini
    model: str = "gpt-5.1"
    system_message: str = "You are AioWeb's helpful AI assistant. Be concise and friendly."


class ChatResponse(BaseModel):
    session_id: str
    response: str


@api_router.post("/ai/chat", response_model=ChatResponse)
async def ai_chat(req: ChatRequest):
    if not EMERGENT_LLM_KEY:
        raise HTTPException(500, "EMERGENT_LLM_KEY not configured on backend")
    session_id = req.session_id or str(uuid.uuid4())
    try:
        chat = LlmChat(
            api_key=EMERGENT_LLM_KEY,
            session_id=session_id,
            system_message=req.system_message,
        ).with_model(req.provider, req.model)
        text = await chat.send_message(UserMessage(text=req.message))
        return ChatResponse(session_id=session_id, response=text)
    except Exception as e:
        logging.exception("ai_chat failed")
        raise HTTPException(500, f"AI chat failed: {e}")


# ------- AI: Image generation (Nano Banana) -------
class ImageRequest(BaseModel):
    prompt: str
    model: str = "gemini-3.1-flash-image-preview"


class ImageResponse(BaseModel):
    text: str
    images: List[str]   # list of base64 PNG strings (no data: prefix)
    mime_type: str = "image/png"


@api_router.post("/ai/image", response_model=ImageResponse)
async def ai_image(req: ImageRequest):
    if not EMERGENT_LLM_KEY:
        raise HTTPException(500, "EMERGENT_LLM_KEY not configured")
    try:
        chat = LlmChat(
            api_key=EMERGENT_LLM_KEY,
            session_id=str(uuid.uuid4()),
            system_message="You are an image generation assistant.",
        ).with_model("gemini", req.model).with_params(modalities=["image", "text"])
        text, images = await chat.send_message_multimodal_response(UserMessage(text=req.prompt))
        b64_list: List[str] = []
        mime = "image/png"
        if images:
            for img in images:
                b64_list.append(img.get('data', ''))
                mime = img.get('mime_type', mime)
        return ImageResponse(text=text or "", images=b64_list, mime_type=mime)
    except Exception as e:
        logging.exception("ai_image failed")
        raise HTTPException(500, f"AI image gen failed: {e}")


# ------- AI: NSFW image generation via fal.ai -------
# The Emergent Universal Key (Nano Banana / GPT-Image-1) blocks NSFW prompts.
# fal.ai exposes uncensored SDXL/RealVis models. Users supply their own fal.ai
# API key on-device (Settings → fal.ai key) — we just proxy the request so the
# key never leaves their network in plaintext.
class NsfwImageRequest(BaseModel):
    prompt: str
    fal_key: str
    model: str = "fal-ai/fast-sdxl"  # standard SDXL via fal.ai; supports enable_safety_checker=false
    image_size: str = "square_hd"
    num_inference_steps: int = 28
    negative_prompt: Optional[str] = "blurry, low quality, distorted, watermark"


@api_router.post("/ai/image_nsfw", response_model=ImageResponse)
async def ai_image_nsfw(req: NsfwImageRequest):
    if not req.fal_key.strip():
        raise HTTPException(400, "fal.ai API key required. Get one free at fal.ai/dashboard then add it in Settings.")
    payload = {
        "prompt": req.prompt,
        "image_size": req.image_size,
        "num_inference_steps": req.num_inference_steps,
        "enable_safety_checker": False,
    }
    if req.negative_prompt:
        payload["negative_prompt"] = req.negative_prompt
    try:
        async with httpx.AsyncClient(timeout=120) as cx:
            r = await cx.post(
                f"https://fal.run/{req.model}",
                headers={
                    "Authorization": f"Key {req.fal_key.strip()}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )
            if r.status_code != 200:
                raise HTTPException(r.status_code, f"fal.ai returned HTTP {r.status_code}: {r.text[:300]}")
            data = r.json()
            urls = [img.get("url") for img in data.get("images", []) if img.get("url")]
            if not urls:
                raise HTTPException(500, "fal.ai response had no images")
            # Download each image and return as base64 (keeps the client interface identical
            # to /api/ai/image — the Compose ImageBitmap decoder is already wired for this).
            b64_list: List[str] = []
            for u in urls:
                ir = await cx.get(u)
                if ir.status_code == 200:
                    b64_list.append(base64.b64encode(ir.content).decode("ascii"))
            mime = "image/png"
            return ImageResponse(
                text=f"Generated via {req.model} on fal.ai",
                images=b64_list,
                mime_type=mime,
            )
    except HTTPException:
        raise
    except Exception as e:
        logging.exception("ai_image_nsfw failed")
        raise HTTPException(500, f"NSFW image gen failed: {e}")


# ------- TMDB proxy (keeps API key off-device, optional) -------
TMDB_API_KEY = os.environ.get('TMDB_API_KEY', '')  # optional; if blank, app uses a public read key
TMDB_BASE = "https://api.themoviedb.org/3"


@api_router.get("/movies/trending")
async def movies_trending(window: str = "week"):
    """Light proxy that lets the app work even if TMDB is blocked. Optional."""
    key = TMDB_API_KEY or "8265bd1679663a7ea12ac168da84d2e8"  # widely-known public sample key
    async with httpx.AsyncClient(timeout=20) as cx:
        r = await cx.get(f"{TMDB_BASE}/trending/movie/{window}", params={"api_key": key})
        return JSONResponse(r.json(), status_code=r.status_code)


app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=os.environ.get('CORS_ORIGINS', '*').split(','),
    allow_methods=["*"],
    allow_headers=["*"],
)

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


@app.on_event("shutdown")
async def shutdown_db_client():
    client.close()
