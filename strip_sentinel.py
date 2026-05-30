import os
import re

def strip_sentinel(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Remove import com.alibaba.csp.sentinel.annotation.SentinelResource;
    content = re.sub(r'import com\.alibaba\.csp\.sentinel\.annotation\.SentinelResource;\n?', '', content)
    
    # Remove @SentinelResource(...) annotations
    content = re.sub(r'\s*@SentinelResource\([^)]+\)', '', content)
    
    # Remove @SentinelResource alone
    content = re.sub(r'\s*@SentinelResource', '', content)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

for root, dirs, files in os.walk('services'):
    for name in files:
        if name.endswith('.java'):
            strip_sentinel(os.path.join(root, name))
