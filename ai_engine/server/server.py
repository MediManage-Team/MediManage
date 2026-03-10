import os
import sys

# Ensure the root directory is in sys.path so app can be imported
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.main import main  # type: ignore

if __name__ == "__main__":
    main()
