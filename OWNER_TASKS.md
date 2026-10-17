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

## 3. Accept the Gemma license on Hugging Face (needed in Phase 1, not yet)
- [ ] Done

1. Create a free account at https://huggingface.co/join (if you don't have one).
2. Claude will give you the exact model page link when Phase 1 starts. On that page, click **Agree and access repository** and accept Google's Gemma terms.
3. Download the model file Claude names (about 4.9 GB for the main model) into your PC's `Downloads` folder. Make sure the phone has at least 8 GB free storage.

**Where it goes:** the file stays on your PC/phone only. Never commit it to the repo (it's in `.gitignore`).

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
