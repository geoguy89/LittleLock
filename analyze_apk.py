from androguard.core.apk import APK
import os, sys

apk_path = r'C:\Projects\LittleLock\base.apk'
a = APK(apk_path)

print('=== PACKAGE INFO ===')
print(f'Package: {a.get_package()}')
print(f'App name: {a.get_app_name()}')
print(f'Version: {a.get_androidversion_name()} ({a.get_androidversion_code()})')
print(f'Target SDK: {a.get_target_sdk_version()}')
print(f'Min SDK: {a.get_min_sdk_version()}')
print(f'Type: {a.get_androidversion_type()}')

print()
print('=== PERMISSIONS ===')
for perm in sorted(a.get_permissions()):
    print(f'  {perm}')

print()
print('=== ACTIVITIES ===')
for activity in a.get_activities():
    print(f'  {activity}')

print()
print('=== SERVICES ===')
for service in a.get_services():
    print(f'  {service}')

print()
print('=== RECEIVERS ===')
for receiver in a.get_receivers():
    print(f'  {receiver}')

print()
print('=== PROVIDERS ===')
for provider in a.get_providers():
    print(f'  {provider}')

print()
print('=== MAIN ACTIVITY ===')
print(f'  {a.get_main_activity()}')

print()
print('=== NATIVE LIBS ===')
for lib in a.get_files():
    if lib.startswith('lib/'):
        print(f'  {lib}')

print()
print('=== APK SIZE ===')
print(f'  {os.path.getsize(apk_path) / (1024*1024):.1f} MB')

print()
print('=== INTENT FILTERS ===')
for activity in a.get_activities():
    filters = a.get_activity_intent_filters(activity)
    if filters:
        print(f'  Activity: {activity}')
        for f in filters:
            print(f'    Action: {f.get("action", [])}')
            print(f'    Category: {f.get("category", [])}')
            print(f'    Data: {f.get("data", [])}')

print()
print('=== SERVICE INTENT FILTERS ===')
for service in a.get_services():
    filters = a.get_service_intent_filters(service)
    if filters:
        print(f'  Service: {service}')
        for f in filters:
            print(f'    Action: {f.get("action", [])}')
            print(f'    Category: {f.get("category", [])}')
            print(f'    Data: {f.get("data", [])}')

print()
print('=== RECEIVER INTENT FILTERS ===')
for receiver in a.get_receivers():
    filters = a.get_receiver_intent_filters(receiver)
    if filters:
        print(f'  Receiver: {receiver}')
        for f in filters:
            print(f'    Action: {f.get("action", [])}')
            print(f'    Category: {f.get("category", [])}')
            print(f'    Data: {f.get("data", [])}')
