# Intel local AI runtime

The production default is Ollama on the host Intel Arc GPU. Run:

```powershell
.\scripts\ai\start-intel-ollama.ps1
```

The script persists `OLLAMA_VULKAN=1` and `OLLAMA_IGPU_ENABLE=1`, starts Ollama
when needed, warms `electrahub-sparky:4b`, and verifies the loaded processor.

OpenVINO Model Server is an optional provider for comparison. Install the OVMS
Windows binary so `ovms.exe` is on `PATH`, then run:

```powershell
.\scripts\ai\start-openvino-ovms.ps1 -Device GPU
```

After `/v3/models` reports the configured model, set `AI_OVMS_ENABLED=true`.
The AI service provider chain tries `ovms` before Ollama and preserves Ollama as
the fallback. Use `-Device NPU` only for a separately benchmarked NPU-compatible
model; the Arc GPU is the preferred default for concurrent text generation.
