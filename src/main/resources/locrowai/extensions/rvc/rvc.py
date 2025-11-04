from rvc_python.infer import RVCInference

rvc = RVCInference(device="cuda:0")
rvc.set_params(
    f0method="pm",
    filter_radius=2,
    rms_mix_rate=0.5,
    f0up_key=-3
)