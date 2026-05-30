import os
import xml.etree.ElementTree as ET

def remove_dependencies():
    for root, dirs, files in os.walk('.'):
        for file in files:
            if file == 'pom.xml':
                filepath = os.path.join(root, file)
                try:
                    tree = ET.parse(filepath)
                    root_elem = tree.getroot()
                    ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}
                    ET.register_namespace('', 'http://maven.apache.org/POM/4.0.0')
                    
                    modified = False
                    
                    # Remove from dependencyManagement
                    for dep_mgmt in root_elem.findall('mvn:dependencyManagement/mvn:dependencies/mvn:dependency', ns):
                        artifact = dep_mgmt.find('mvn:artifactId', ns)
                        if artifact is not None and artifact.text in ['spring-cloud-dependencies', 'spring-cloud-alibaba-dependencies']:
                            root_elem.find('mvn:dependencyManagement/mvn:dependencies', ns).remove(dep_mgmt)
                            modified = True
                            
                    # Remove from dependencies
                    for deps in root_elem.findall('mvn:dependencies', ns):
                        for dep in deps.findall('mvn:dependency', ns):
                            artifact = dep.find('mvn:artifactId', ns)
                            if artifact is not None and artifact.text in [
                                'spring-cloud-starter-alibaba-nacos-config', 
                                'spring-cloud-starter-alibaba-nacos-discovery',
                                'spring-cloud-starter-openfeign'
                            ]:
                                deps.remove(dep)
                                modified = True
                                
                    if modified:
                        tree.write(filepath, xml_declaration=True, encoding='UTF-8')
                        print(f"Updated {filepath}")
                except Exception as e:
                    print(f"Error processing {filepath}: {e}")

remove_dependencies()
