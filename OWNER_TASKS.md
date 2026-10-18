# Owner tasks

Things only you (the owner) can do. Claude adds new ones here as soon as they come up and ticks them off once you confirm they're done.
Never paste keys, tokens or passwords into the chat or into any file in this repo.

---

## 1. Install Android Studio  — **needed now (blocks building the app)**
- [x] Done (2026-10-17 — SDK Platform 37 and build-tools 36.0.0 confirmed installed)

1. Open https://developer.android.com/studio in your browser.
2. Click **Download Android Studio**, tick the box to accept the terms, and click download.
3. Run the downloaded installer. Keep every default option (including "Android Virtual Device" — harmless) and click **Next** until it installs.
4. Open Android Studio. The **Setup Wizard** appears: choose **Standard**, keep the default theme, and click **Next**.
5. On the **License Agreement** screen, select each license on the left and click **Accept** for each, then **Finish**. This downloads the Android SDK (a few GB; can take 10–30 minutes).
6. When it finishes, on the Welcome screen click **More Actions → SDK Manager**. On the **SDK Platforms** tab make sure **Android 17 (API 37)** is ticked (tick it if not), click **Apply**, accept the license, and wait for the download to finish. Click **OK**.
7. Close Android Studio.

**How to check it worked:** open File Explorer and go to `C:\Users\moham\AppData\Local\Android\Sdk`. You should see folders named `platform-tools` and `platforms`, and inside `platforms` a folder named `android-37`.

Tell Claude "Android Studio is installed" so the build can be checked.

---

## 2. Prepare your Poco F5 for installing the app
- [x] Done (2026-10-17 — Android 15, paired over Wireless debugging; Wireless debugging can stay off until Claude asks)

**Still needed before the first install (Phase 1):** in **Developer options**, turn on **Install via USB** (works over Wi-Fi too; Xiaomi may ask for a Mi account sign-in). When Claude installs, a popup appears on the phone — tap **Install**.

1. On the phone: **Settings → About phone**. Note the **Android version** (tell Claude what it says).
2. Tap **OS version** (or "HyperOS version") **7 times** quickly until it says "You are now a developer".
3. Go back to **Settings → Additional settings → Developer options**.
4. Turn on **USB debugging**. On Xiaomi phones also turn on **Install via USB** (it may ask you to sign in to a Mi account — that's a Xiaomi requirement, not ours).
5. Plug the phone into the PC with a USB cable. On the phone, a popup asks "Allow USB debugging?" — tick **Always allow from this computer** and tap **Allow**.

**How to check it worked:** Claude will run a check that lists your phone. You can also open Android Studio → the device dropdown at the top should show "Xiaomi 23049PCD8G" (or similar).

---

## 3. ~~Accept the Gemma license on Hugging Face~~ — no longer needed
- [x] Not needed (2026-10-18): the chosen models (Gemma 4 E4B/E2B) are open (Apache-2.0) with no sign-in. Claude downloaded them to the PC's `M3\models` folder (ignored by git) and checked their checksums.

---

## 4. Get the phone ready for the AI model test — **needed for the next step**
- [ ] Done

1. **Free space:** on the phone, open **Settings → About phone → Storage**. You need at least **8 GB free** (both test models together are about 6.3 GB). If you have less, delete something or tell Claude, who will test one model at a time.
2. **Install via USB:** **Settings → Additional settings → Developer options** → turn on **Install via USB**. Xiaomi may ask you to sign in to your Mi account. That's Xiaomi's rule, not ours.
3. **Wireless debugging:** in the same Developer options screen, turn on **Wireless debugging**. Your PC and phone must be on the same Wi-Fi.
4. Tap **Wireless debugging** (the words, not the switch). If Claude asks for it, tap **Pair device with pairing code** and send Claude the **IP address & port** and the **6-digit code** shown. Never send passwords.
5. Keep the phone **plugged into a charger** and **unlocked** during the test. Copying the model takes about 5–15 minutes over Wi-Fi, and the benchmark takes 5–15 more. A USB-C cable makes the copy much faster.
6. When the popup **"Install M³?"** appears on the phone, tap **Install**.

**What Claude will do on the phone (and only this):**
- Install M³.
- Copy the two model files into M³'s private folder. No files go anywhere else, and nothing is left in your Downloads or other folders.
- Run the benchmark.
- Close M³ and disconnect.

Nothing else on the phone is touched, and no settings are changed. Uninstalling M³ removes everything.

**How to check it worked:** Claude will tell you the results. You'll also see M³ in your app drawer.

---

## 5. Make the GitHub repo public (later, when you decide)
- [ ] Done

The license is already in place (`LICENSE`, PolyForm Noncommercial 1.0.0): anyone can view, download and use the code non-commercially; commercial use needs your permission. Before flipping the switch:

1. **Decide about your email address.** Every commit so far records `m.abdurazak.h@gmail.com`, and that becomes visible once the repo is public. Your options:
   - **Keep it.** Nothing to do.
   - **Hide it for future commits.** On GitHub, go to **Settings → Emails**, tick **Keep my email addresses private**, and copy the `…@users.noreply.github.com` address shown. Tell Claude, who will set it for this repo. Old commits keep the Gmail address.
   - **Hide it everywhere.** Claude rewrites the history to use the noreply address. This can't be undone and needs a force-push, so Claude only does it if you say so explicitly.
2. Tell Claude "ready to go public". Claude re-checks the repo for secrets or personal data and confirms nothing private is in the history. The AI model files are never in the repo.
3. To make it public yourself: open https://github.com/M7md-AR-H/M3 → **Settings** → scroll to **Danger Zone** → **Change repository visibility** → **Make public**, then confirm. (Claude can also do this with your OK.)

**How to check it worked:** open https://github.com/M7md-AR-H/M3 in a private/incognito browser window. You should see the code and "PolyForm Noncommercial" as the license.

**Note:** a license is a legal tool, and Claude isn't a lawyer. If someone uses the code commercially, enforcing it is up to you.

---

## Try it yourself

### Phase 0 — app shell (Claude verified this on the emulator on 2026-10-17; run it on your phone once the app is first installed in Phase 1)
1. Find **M³** in your app drawer and open it. **Expect:** an off-white screen with a big "M³" masthead and five tabs at the bottom: TODAY, TRAIN, FUEL, YOU, COACH (COACH in red). **Broken if:** it crashes, or text is unreadable.
2. Tap each tab. **Expect:** each shows a title and "Coming in Phase 1". Today shows five placeholder cards.
3. Press the phone's **recent apps** button. **Expect:** M³'s preview is blank/black (privacy). Try a screenshot inside the app. **Expect:** it's blocked.
4. Go to **YOU → Customize**:
   - Tap **Dark**. **Expect:** the whole app turns dark. Tap **Light** to go back.
   - Tap a different **accent** swatch. **Expect:** the COACH tab and buttons change colour.
   - Switch font to **Clean**. **Expect:** the serif headlines become plain sans-serif.
   - Drag **Text size** and **Corner radius**. **Expect:** text grows/shrinks; cards get rounded corners.
   - Hide a Today card with its switch, and move one with Up/Down. Go to TODAY. **Expect:** the change shows there.
   - Tap **Undo**. **Expect:** your last change is reversed. Tap **Reset to default**. **Expect:** everything returns to the original look.
5. Still in Customize, tap **Simulate a Coach change**. **Expect:** a message "Coach moved Fuel to the top" with **Undo**; the Fuel card is now first on TODAY. Tap Undo. **Expect:** it moves back.
6. Turn off **"Let agents change: Today layout"**, then tap **Simulate a Coach change** again. **Expect:** "Coach can't change Today — you locked it", and nothing moves.
7. Close the app fully and reopen it. **Expect (known limitation):** customizations are back to default — saving them arrives in Phase 1.
