FROM postgres:18-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*
COPY tools/backup/transcreve_backup.py /tools/backup.py
ENTRYPOINT ["python3", "/tools/backup.py"]
