import yaml

def remove_nacos(filepath):
    try:
        with open(filepath, 'r') as f:
            data = yaml.safe_load(f)
            
        if 'services' in data and 'nacos' in data['services']:
            del data['services']['nacos']
            
        if 'volumes' in data:
            if 'nacos_data' in data['volumes']:
                del data['volumes']['nacos_data']
            if 'nacos_logs' in data['volumes']:
                del data['volumes']['nacos_logs']
                
        # Also clean up depends_on nacos if any
        if 'services' in data:
            for s_name, s_data in data['services'].items():
                if 'depends_on' in s_data:
                    if isinstance(s_data['depends_on'], list):
                        if 'nacos' in s_data['depends_on']:
                            s_data['depends_on'].remove('nacos')
                    elif isinstance(s_data['depends_on'], dict):
                        if 'nacos' in s_data['depends_on']:
                            del s_data['depends_on']['nacos']
                            
        with open(filepath, 'w') as f:
            yaml.dump(data, f, default_flow_style=False, sort_keys=False)
        print(f"Updated {filepath}")
    except Exception as e:
        print(f"Error updating {filepath}: {e}")

remove_nacos('deploy/docker-compose.yml')
remove_nacos('deploy/docker-compose.prod.yml')
