import os
import re

def strip_annotations(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Remove Spring MVC annotations
    content = re.sub(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)\([^)]*\)', '', content)
    content = re.sub(r'@(PathVariable|RequestBody|RequestParam|RequestHeader)(\([^)]*\))?', '', content)

    with open(filepath, 'w') as f:
        f.write(content)
    print(f"Stripped {filepath}")

api_dir = 'libs/lumira-api/src/main/java/com/lumira/api/client'
for file in os.listdir(api_dir):
    if file.endswith('.java'):
        strip_annotations(os.path.join(api_dir, file))
