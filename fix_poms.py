import os
import xml.etree.ElementTree as ET

def fix_pom(file_path):
    ET.register_namespace('', 'http://maven.apache.org/POM/4.0.0')
    tree = ET.parse(file_path)
    root = tree.getroot()
    ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}
    
    modified = False
    dependencies = root.find('mvn:dependencies', ns)
    if dependencies is not None:
        to_remove = []
        for dep in dependencies.findall('mvn:dependency', ns):
            group_id = dep.find('mvn:groupId', ns)
            artifact_id = dep.find('mvn:artifactId', ns)
            
            # Remove empty alibaba cloud dependencies (where artifactId is missing or null)
            if group_id is not None and group_id.text == 'com.alibaba.cloud':
                if artifact_id is None or artifact_id.text is None:
                    to_remove.append(dep)
                elif 'sentinel' in artifact_id.text or 'nacos' in artifact_id.text:
                    to_remove.append(dep)
        
        for dep in to_remove:
            dependencies.remove(dep)
            modified = True
            
    if modified:
        tree.write(file_path, xml_declaration=True, encoding='UTF-8')
        print(f"Fixed {file_path}")

for root, dirs, files in os.walk('.'):
    for name in files:
        if name == 'pom.xml' and 'target' not in root:
            fix_pom(os.path.join(root, name))
