from pathlib import Path
import importlib

extensions = Path('extensions').glob('*/__init__.py')

for extension in extensions:
    print('[LOAD] Loading extension: ' + extension.parent.name)
    importlib.import_module(f'extensions.{extension.parent.name}')