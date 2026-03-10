import logging
import os
from flask import Flask
from app.core.logger import configure_structured_logging
from app.api.routes import api_bp, MODELS_DIR
from app.api.middleware import setup_middleware, ADMIN_TOKEN

def create_app():
    configure_structured_logging(force=True)
    logger = logging.getLogger(__name__)

    app = Flask(__name__)
    
    setup_middleware(app)
    app.register_blueprint(api_bp)

    return app

app = create_app()

def main():
    logger = logging.getLogger(__name__)
    logger.info("Starting AI Engine Server on port 5000...")
    logger.info(f"Models directory: {MODELS_DIR}")
    logger.info("Admin token protection: %s", "enabled" if ADMIN_TOKEN else "missing")
    os.makedirs(MODELS_DIR, exist_ok=True)
    app.run(host='127.0.0.1', port=5000)

if __name__ == '__main__':
    main()
