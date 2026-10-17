# LittleLock - acceptance tests

What "working" means, and the test that proves each part (`tools\check.ps1` runs them all; every one must pass).

## The child's home screen
- Shows only the apps the parent allowed, sorted by name - `the_home_screen_layout_and_app_drawer_functionality`,
  `only_allowed_apps_are_shown_sorted_by_name`
- Tapping an app opens it - `the_home_screen_layout_and_app_drawer_functionality`
- Settings and the Play Store never show unless the parent allows them - `the_user_interface_is_intuitive_for_children`,
  `settings_and_play_store_are_hidden_unless_the_parent_allows_them`
- With nothing allowed the child sees a friendly message, not a blank screen - `with_nothing_allowed_the_child_sees_a_message_not_an_empty_screen`
- Back doesn't leave the home screen - `back_does_not_leave_the_home_screen`
- Changes to the allowed list show up when the child comes back - `the_grid_picks_up_changes_to_the_allowed_list`
- LittleLock branding - `the_app_launches_and_displays_a_custom_LittleLock_branding`, `testAppNameIsLittleLock`

## Parent access
- First run asks the parent to set a PIN - `on_first_run_the_parent_is_asked_to_set_a_pin`
- Setting a PIN needs it typed twice - `setting_a_pin_needs_it_typed_twice`
- The lock button opens the PIN pad - `the_launcher_handles_core_interactions_without_crashes`
- The right PIN opens parent settings; a wrong one doesn't - `the_right_pin_opens_parent_settings`, `a_wrong_pin_keeps_settings_shut`
- Parent settings refuse to open without the PIN - `parent_settings_will_not_open_without_the_pin`
- Changing the PIN needs the old one - `changing_the_pin_without_being_signed_in_asks_for_the_old_one`
- Five wrong PINs lock the pad for 30 s - `five_wrong_pins_lock_the_pad_for_thirty_seconds`
- The PIN is stored salted and hashed, never in plain text; an old plain PIN is migrated - `the_pin_is_stored_hashed_never_in_plain_text`,
  `a_plain_pin_from_an_older_version_still_works_and_gets_hashed`, `the_right_pin_verifies_and_a_wrong_one_does_not`

## Kid mode
- Apps that aren't allowed are sent back home; allowed apps, keyboards and the system UI never are - `an_app_that_is_not_allowed_is_sent_home`,
  `allowed_apps_littlelock_keyboards_and_the_system_ui_are_never_blocked`
- Nothing is blocked with kid mode off or while the parent is signed in - `nothing_is_blocked_with_kid_mode_off_or_while_the_parent_is_signed_in`
- The parent can turn kid mode off and on; leaving settings locks again - `parent_settings_turns_kid_mode_off_and_back_on`

## Notifications and privacy
- Discord webhook and notification settings - `the_parent_configures_discord_webhook`, `the_parent_modifies_notification_settings`,
  `the_parent_receives_discord_notification_on_usage`, `the_webhook_url_is_remembered_and_can_be_cleared`
- No servers but the parent's own webhook; no tracking, ads or paywall - `the_app_makes_no_external_network_requests_to_original_servers`,
  `testOnlyDiscordWebhookMakesHttp`, `the_app_collects_no_unauthorized_personal_data`, `the_app_has_no_paywall_prompts`
- Only the permissions a launcher needs - `the_app_respects_system_permissions`

## Release
- The release APK is signed with the one release key (see README, "Release signing") - `the_generated_apk_is_signed_and_verifiable`
  (runs once a release APK is built)

## Checked by hand on a device (no test can)
- After **Make LittleLock the home screen**, the Home button always returns to LittleLock.
- With blocking (Accessibility) on, opening a not-allowed app from a notification bounces back home within a second.
