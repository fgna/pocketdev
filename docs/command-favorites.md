# Project command favorites

In the Command section, enter or paste a command block and tap the outlined star to save it for the selected project. A filled star means that the exact block is already saved. Tap it again to remove the favorite.

Open **Favorites** to see saved blocks for this project. Tap a block to load its complete text into the command input, review it, then tap **Run**. Loading or starring a block never starts a command. A filled star in the list removes that entry. Favorites remain available after restarting PocketDev.

Favorites store the exact text locally as part of the project configuration. They do not capture stdin replies or command output. Avoid putting passwords or tokens in saved commands.

## Device acceptance for #116

1. In project A, paste a multi-line block with quotes and pipes. Tap its star twice to save and remove it, then save it again. Confirm the count and star state update.
2. Clear the input, open Favorites and select the block. Confirm all lines and whitespace are preserved and nothing runs until Run is tapped.
3. Switch to project B. Confirm A's favorite is absent. Save a different block in B, return to A and confirm its original list remains intact.
4. Restart the app and check both lists. Edit A's name or path and save; its favorites must remain intact.
5. Remove A's favorite from the list. Restart and confirm it stays removed while B's favorite remains.
6. Run a familiar command and check streaming output and Stop. While work is active, selecting a favorite must not replace the running command.

Automated coverage checks exact text round trips, per-project isolation, duplicate prevention, removal persistence, blank commands and compatibility with older project records.
