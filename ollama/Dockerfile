FROM ollama/ollama:latest
EXPOSE 11434
ENTRYPOINT ["/bin/sh", "-c", "ollama serve & pid=$!; until ollama list >/dev/null 2>&1; do sleep 2; done; ollama pull qwen2.5-coder:0.5b; wait $pid"]
