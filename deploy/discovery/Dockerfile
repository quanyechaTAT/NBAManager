FROM python:3.12-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*
COPY backend/scripts/requirements-discovery.txt .
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu && pip install --no-cache-dir -r requirements-discovery.txt
COPY backend/scripts/discovery_app ./discovery_app
ENV PYTHONUNBUFFERED=1
EXPOSE 19001
CMD ["uvicorn", "discovery_app.api:app", "--host", "0.0.0.0", "--port", "19001"]
