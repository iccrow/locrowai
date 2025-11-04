from pydantic import BaseModel, ConfigDict
import torch
from torch import Tensor
import torchaudio

from api import Function, register
from .embed_model import model

class EmbedParams(BaseModel):
    path: str

class EmbedReturns(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    embeddings: Tensor

@register("/audio/embed")
class EmbedFunc(Function[EmbedParams, EmbedReturns]):

    def exec(self):
        signal, fs = torchaudio.load(self.params.path)
        embeddings = model.encode_batch(signal)

        self.returns = EmbedReturns(embeddings=embeddings)
    
    @classmethod
    def warmup(cls):
        dummy_signal = torch.zeros((1, 16000))
        _ = model.encode_batch(dummy_signal)