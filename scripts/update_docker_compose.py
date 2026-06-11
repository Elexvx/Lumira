import yaml
from copy import deepcopy

def update_docker_compose(filepath):
    try:
        with open(filepath, 'r') as f:
            data = yaml.safe_load(f)

        services_to_remove = [
            'gateway-service',
            'auth-service',
            'system-service',
            'file-service',
            'message-service',
            'plugin-service',
            'localization-service',
            'job-executor'
        ]

        # Use system-service config as template for lumira-server
        if 'services' in data and 'system-service' in data['services']:
            lumira_server_config = deepcopy(data['services']['system-service'])
            lumira_server_config['image'] = 'lumira/lumira-server:0.1.0'
            lumira_server_config['container_name'] = 'lumira-server'
            # Adjust ports if necessary, usually it doesn't map directly in prod if using api-proxy
            # Remove from original services
            for svc in services_to_remove:
                if svc in data['services']:
                    del data['services'][svc]
            
            data['services']['lumira-server'] = lumira_server_config

        # Update depends_on
        if 'services' in data:
            for s_name, s_data in data['services'].items():
                if 'depends_on' in s_data:
                    if isinstance(s_data['depends_on'], list):
                        new_deps = []
                        for dep in s_data['depends_on']:
                            if dep in services_to_remove:
                                if 'lumira-server' not in new_deps:
                                    new_deps.append('lumira-server')
                            else:
                                new_deps.append(dep)
                        s_data['depends_on'] = new_deps
                    elif isinstance(s_data['depends_on'], dict):
                        new_deps = {}
                        for dep, cfg in s_data['depends_on'].items():
                            if dep in services_to_remove:
                                new_deps['lumira-server'] = cfg
                            else:
                                new_deps[dep] = cfg
                        s_data['depends_on'] = new_deps

        with open(filepath, 'w') as f:
            yaml.dump(data, f, default_flow_style=False, sort_keys=False)
        print(f"Updated {filepath}")
    except Exception as e:
        print(f"Error updating {filepath}: {e}")

update_docker_compose('deploy/docker-compose.prod.yml')
