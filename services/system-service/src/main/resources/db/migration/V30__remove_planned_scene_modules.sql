delete from platform_module_dependency
where module_code in ('journal', 'competition')
   or dependency_module_code in ('journal', 'competition');

delete from platform_module_definition
where module_code in ('journal', 'competition');
