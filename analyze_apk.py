from androguard.core.apk import APK
import os, sys, logging

# Suppress debug output
logging.getLogger('androguard').setLevel(logging.ERROR)

apk_path = r'C:\Projects\LittleLock\base.apk'
a = APK(apk_path)

results = []

results.append('=== PACKAGE INFO ===')
results.append(f'Package: {a.get_package()}')
results.append(f'App name: {a.get_app_name()}')
results.append(f'Version: {a.get_androidversion_name()} ({a.get_androidversion_code()})')
results.append(f'Target SDK: {a.get_target_sdk_version()}')
results.append(f'Min SDK: {a.get_min_sdk_version()}')

results.append('')
results.append('=== PERMISSIONS ===')
for perm in sorted(a.get_permissions()):
    results.append(f'  {perm}')

results.append('')
results.append('=== ACTIVITIES ===')
for activity in a.get_activities():
    results.append(f'  {activity}')

results.append('')
results.append('=== SERVICES ===')
for service in a.get_services():
    results.append(f'  {service}')

results.append('')
results.append('=== RECEIVERS ===')
for receiver in a.get_receivers():
    results.append(f'  {receiver}')

results.append('')
results.append('=== PROVIDERS ===')
for provider in a.get_providers():
    results.append(f'  {provider}')

results.append('')
results.append('=== MAIN ACTIVITY ===')
results.append(f'  {a.get_main_activity()}')

results.append('')
results.append('=== APK SIZE ===')
results.append(f'  {os.path.getsize(apk_path) / (1024*1024):.1f} MB')

results.append('')
results.append('=== INTENT FILTERS ===')
for activity in a.get_activities():
    filters = a.get_activity_intent_filters(activity)
    if filters:
        results.append(f'  Activity: {activity}')
        for f in filters:
            results.append(f'    Action: {f.get("action", [])}')
            results.append(f'    Category: {f.get("category", [])}')
            results.append(f'    Data: {f.get("data", [])}')

results.append('')
results.append('=== SERVICE INTENT FILTERS ===')
for service in a.get_services():
    filters = a.get_service_intent_filters(service)
    if filters:
        results.append(f'  Service: {service}')
        for f in filters:
            results.append(f'    Action: {f.get("action", [])}')
            results.append(f'    Category: {f.get("category", [])}')
            results.append(f'    Data: {f.get("data", [])}')

results.append('')
results.append('=== RECEIVER INTENT FILTERS ===')
for receiver in a.get_receivers():
    filters = a.get_receiver_intent_filters(receiver)
    if filters:
        results.append(f'  Receiver: {receiver}')
        for f in filters:
            results.append(f'    Action: {f.get("action", [])}')
            results.append(f'    Category: {f.get("category", [])}')
            results.append(f'    Data: {f.get("data", [])}')

# Write to file
with open(r'C:\Projects\LittleLock\apk_analysis.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(results))

print('\n'.join(results))
