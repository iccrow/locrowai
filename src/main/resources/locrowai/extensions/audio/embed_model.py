import torchaudio
from speechbrain.inference.speaker import EncoderClassifier

model = EncoderClassifier.from_hparams(source="speechbrain/spkrec-ecapa-voxceleb")