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

## 4. Get the phone ready for the AI model test
- [x] Done (2026-10-18 — app installed, models copied over USB, benchmark run)

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

## 6. Continue the work in a Claude Code cloud session (optional)
- [ ] Not needed for now: you chose to continue locally on 2026-10-30. Kept in case you want it later.

A cloud session runs Claude on a machine in the cloud, so it keeps working after you close your laptop. It uses your claude.ai plan's usage (no separate charge for the machine). It works from the GitHub repo, so it can't see this PC or your phone.

**A. Connect GitHub (once)**
1. Open https://claude.ai/code and sign in with the account that has your plan.
2. Click the prompt to connect GitHub and approve it.
3. M3 is private, so also open https://github.com/apps/claude/installations/new, choose **M7md-AR-H**, select **Only select repositories**, pick **M3**, and click **Install**.

**B. Set up the environment (once)**
1. At https://claude.ai/code, open the environment selector under the message box and edit **Default** (or add a new one named `M3 Android`).
2. **Network access:** choose **Custom**, keep the option that includes the default list ticked, and add this one domain: `dl.google.com`
3. **Environment variables:** add these two lines:
   ```
   ANDROID_HOME=/root/android-sdk
   BASH_MAX_TIMEOUT_MS=1200000
   ```
4. **Setup script:** open `scripts/cloud-setup.sh` on GitHub (branch `feat/ui-editorial-pass`), copy all of it and paste it into the box.
5. Save.

**C. Start the session**
1. In the box at https://claude.ai/code, pick the repo **M3** and set the branch to `feat/ui-editorial-pass`.
2. Set the mode to **Auto** if it's offered, otherwise **Accept edits**.
3. Paste the prompt from HANDOFF.md → "Cloud session prompt" and send.
4. You can close the tab. Check it at claude.ai/code or in the Claude app's **Code** tab.

**How to check it worked:** within about 15 minutes Claude reports that `./gradlew test lint :app:assembleDebug` passed. If it says a download was blocked, check step B2. If the session won't start, the setup script failed: remove it, start a session, and ask Claude to run it by hand.

**Bring the work home:** on this PC, in the M3 folder, run `git fetch` and `git switch <the session's branch>`, then tell local Claude "install on the phone and run the check". Anything that needs the phone, the emulator or the Coach model happens here.

---

## Try it yourself

### Snap meal (needs your phone: the emulator has no AI model)
1. Open **FUEL** and tap **Snap meal**, then **Take a photo**. Photograph a plate and confirm.
2. **Expect:** "Looking at your plate…" for up to a minute, then **Your plate**, with a name and numbers filled in, one line of critique, and a red note that it's an estimate.
3. Change a number if it looks wrong, then **Save**. **Expect:** it's on Today's plates.
4. Photograph something that isn't food. **Expect:** "I can't see food in that photo."
5. **Broken if:** anything saves without Save, or a photo appears in your gallery from M³.

### Progress (Claude checked this on the emulator on 2026-10-30)
1. Open **YOU**. **Expect:** Weight at the top. Enter today's weight and tap **Log**.
2. Log again over the next days. **Expect:** a chart with a thin line for weigh-ins and a thicker smooth line for the trend. After a week, a weekly change appears next to the trend.
3. Tap **Add measurements**, fill in any of the four, then **Save**. **Expect:** the values on YOU; measure again another day and the change shows.
4. **FUEL:** after logging your weight, the calorie target follows the trend slowly, not each day's jump.
5. **Broken if:** a typo such as 8 kg is accepted, or the chart looks wrong after several days.

### Fuel (Claude checked this on the emulator on 2026-10-30)
1. Open **FUEL**. **Expect:** a daily calorie target with protein, carbs and fat, and a short note on how it's set. Check it's believable for you.
2. Tap **Add food**. Leave everything empty and tap **Save**. **Expect:** red hints. Enter a name and calories, then **Save**. **Expect:** back on Fuel, the entry listed, "kcal left" lower. TODAY's Fuel card shows the same numbers.
3. Tap **×** on the entry. **Expect:** it disappears at once and **Undo** shows. Tap **Undo**: it's back.
4. **Broken if:** the numbers on TODAY and FUEL differ, or the target stays the same after you change your goal in Profile.

### Step counter (only your phone can test this; the emulator has no step sensor)
1. On **TODAY**, find **Steps** under the workout card and tap **Turn on step counting**. **Expect:** Android asks about physical activity. Tap **Allow**. The card shows 0 of 8,000.
2. Walk about 50 steps with M³ open and wait a few seconds. **Expect:** the number goes up. The very first reading only sets a starting point, so earlier steps today don't count.
3. Press Home, walk 50 more steps, and come back. **Expect:** the number catches up within a few seconds.
4. **Broken if:** the number jumps by thousands, goes down, or the app closes. No notification should ever appear for steps.

### UI pass (Claude checked this on the emulator on 2026-10-30; how it feels on your phone is yours to judge)
1. **TODAY:** a large headline such as "Full body C. *Chase the extra rep.*", one boxed workout card, then a dark Editor's note. **Talk to your coach →** opens COACH. **Broken if:** empty boxes appear, or the card shows the wrong day's session.
2. **TRAIN:** seven day squares at the top with today dark and a dot on session days. Tap another day: the page below changes to it. A rest day shows a short note and your next session.
3. **COACH:** your messages are dark blocks, the Coach's answers are in the serif font. Tap **Clear**: the chat disappears and **Chat cleared · Undo** shows. **Undo** brings it back. Tap Clear again and wait 6 seconds: it's gone for good.
4. **Workout:** each set has its kg and reps controls and the Done box on one row; numbers don't jump as the clock runs.
5. **YOU:** rows with arrows at the top; Customize has square switches and a thin slider. In **YOU → Customize**, "Agents may change" replaces the old "Let agents change" labels.
6. **Dark mode:** in Customize tap **Dark**, then check TODAY: the note panel turns light, with a red label.

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
6. Turn off **Agents may change → Today layout**, then tap **Simulate a Coach change** again. **Expect:** "Coach can't change Today — you locked it", and nothing moves.
7. Close the app fully and reopen it. **Expect (since Phase 1b):** your customizations are still there.

### Phase 1a — on-device AI model (installed on your phone 2026-10-18)
1. Open **M³** → **YOU** → scroll to the bottom → tap **AI model & benchmark**. **Expect:** "Gemma 4 E4B" shows **Ready ✓**, and "Gemma 4 E2B" shows "Not on this phone" (removed on purpose after the test). **Broken if:** E4B says "not checked yet" or "Not on this phone".
2. Tap **Run benchmark** with the phone on a charger. **Expect:** "Test 1 of 3…" progress, then three result cards (GPU, GPU + MTP, CPU) after about 5 minutes, each with "JSON pass · photo pass". GPU + MTP should show roughly **15 tok/s**. **Broken if:** the app closes, or a card says "Failed".
3. Tap **Copy full results**, then paste into any notes app. **Expect:** the test answers in plain text (a gym plan, a JSON line, "a red circle and a blue square").
4. Leave the AI model screen with **Back** while a test runs. **Expect:** it stops; nothing keeps running.
5. Check the phone's normal apps still work as usual afterwards. Some apps that were idle in the background may reload when you open them; that's Android freeing memory, not damage.

### Phase 1b — app lock, encrypted storage, onboarding
Before you start: your phone must have a screen lock (PIN, pattern or password). M³ uses it, or your fingerprint, to unlock.

1. Open **M³**. **Expect:** before anything else, the system unlock sheet appears ("Unlock M³") and asks for your fingerprint or PIN. Nothing of the app shows behind it except the "M³ / Locked" page. **Broken if:** you can see any of your data before unlocking.
2. Tap outside the sheet or press Back to cancel it. **Expect:** a "Locked" page with "Authentication canceled. Tap Unlock to try again." and an **Unlock** button. Tap **Unlock** and use your fingerprint or PIN. **Expect:** the app opens.
3. **Onboarding** (first time only). Go through every step:
   - **About you:** tap **Next** with the fields empty. **Expect:** a red hint under each field. Then enter your height in cm, weight in kg (e.g. 82.5), birth year and sex. **Next**.
   - **Goal** and **Experience:** pick one each.
   - **Where you train:** tick **Gym** and/or **Home**; optionally type another place (e.g. "Park") and tap **Add location**.
   - **At Gym / At Home / …:** one page per place. Tick what's there. For dumbbells or kettlebells you can type the weights you have, e.g. `2-24` (2 to 24 kg in 2 kg steps), `5-30/2.5` or `4, 8, 12`. Type something like "lots" to check you get a red hint. **Bodyweight only** clears the list. Add anything missing under **Your own items**.
   - **Your week:** for each day pick **Rest** or a place.
   - **Anything to work around?:** optional free text about injuries. It stays on the phone.
   - **All set:** check the summary, then tap **Start**. **Expect:** the normal TODAY screen.
4. Go to **YOU → Profile**. **Expect:** your answers are filled in. Change your weight and tap **Save**. **Expect:** "Saved".
5. Go to **YOU → Training setup**. **Expect:** your places, ticked equipment and week. Change something, tap **Save**. **Expect:** "Saved".
6. In **YOU → Customize**, switch to **Dark**. Close M³ completely (swipe it away in recent apps) and open it again. **Expect:** unlock sheet again (cold start), then the app opens in **dark mode**, without onboarding, and Profile still shows your new weight. **Broken if:** onboarding appears again or your changes are gone.
7. Open M³, press Home, come back within a minute. **Expect:** no unlock needed. Leave it in the background for more than 5 minutes, then open it. **Expect:** the unlock sheet appears again.
8. Your existing AI model should be untouched: **YOU → AI model & benchmark** still shows **Gemma 4 E4B: Ready ✓**.

### Phase 1c-1 — your coach plans the week (after the next install)
1. Open **M³** → **YOU** → **Training setup** → scroll to **When you can train**. **Expect:** for each day, "Not free" or one of your places. Pick every day you *could* train and where. Tap **Save**.
2. Go to **TRAIN**. **Expect:** a sentence explaining the plan, e.g. "You're free 6 days. For build muscle with some experience, your coach plans 4 sessions a week, with rest days in between: Monday, Tuesday, Thursday, Saturday." Then all seven days: training days as cards (type of session · place), the rest as "Rest". **Broken if:** a session is on a day you marked Not free, or it's empty.
3. Tap a session card. **Expect:** the exercises with sets × reps and rest (e.g. "4 × 6–10 · rest 2 min · Barbell"), only using equipment you ticked for that place. If something isn't possible there, a red line says so (e.g. "No pulling from above exercise possible with the equipment here"). **Broken if:** it asks for equipment you don't have at that place.
4. Go to **TODAY**. **Expect:** the "Today's session" card shows today's workout, or "Rest day" with the next session.
5. Change your goal in **YOU → Profile** (e.g. to Get stronger) and save. **Expect:** TRAIN changes (fewer reps, longer rests on the main lifts).

### Phase 1c-2 — talk to your Coach (after the next install)
The Coach runs on the phone. The first answer after opening M³ takes a little longer while it starts.
1. Open **COACH**. **Expect:** "Runs entirely on this phone…" and three suggestion buttons. **Broken if:** it says to add the AI model (the model should still be there).
2. Tap **Today I only have dumbbells** (or type something like it for a training day). **Expect:** the answer appears word by word, then a card **"Suggested change"**: "Only Dumbbells on <day> (that day only)" with a list like "Monday: Bench press → Dumbbell floor press", and **Apply** / **Not now**.
3. Tap **Apply**. **Expect:** the card says **Applied** with **Undo**; on **TRAIN** that day uses only dumbbells (if it's this week). Tap **Undo**. **Expect:** "Undone", and TRAIN is back to normal.
4. Type **My knee hurts on lunges**. **Expect:** advice (and a suggestion to see a professional for sharp or lasting pain) plus a card "Stop using <a lunge exercise>". Apply it. **Expect:** that exercise disappears from TRAIN.
5. Ask **Why is Wednesday a rest day?** (any rest day). **Expect:** an explanation and **no** suggestion card.
6. Type **Switch to dark mode**. **Expect:** a card; Apply turns the app dark; Undo turns it back.
7. In **YOU → Customize**, turn off **Agents may change → Your plan**, then ask the Coach for an equipment change again. **Expect:** the card says **"Locked by you"** and nothing changes. Turn it back on.
8. Tap **Stop** while the Coach is answering. **Expect:** the answer disappears at once and your question is back in the box; asking again works. Also press Home while it answers and come back: the answer stopped, nothing was saved, other apps stay responsive.
9. **Broken if:** anything changes without you tapping Apply, the app closes by itself, or the Coach invents sets/reps different from TRAIN.

### Phase 1c-2 fixes — re-check (Claude checked these on your phone on 2026-10-21; only no. 6, smoothness, is yours to judge)
1. **Dark mode:** in **COACH**, type **Switch to dark mode**. **Expect:** a card **"Switch the theme to dark"** with **Apply**. Apply turns the app dark with a short fade; Undo turns it back. **Broken if:** no card, or the Coach says it's already done without a note from the app below it.
2. **Locked:** in **YOU → Customize**, turn off **Agents may change → Your plan**. In COACH, type **On Saturday I have no equipment**. **Expect:** a card marked **"Locked by you"**, and nothing changes. Turn the lock back on.
3. **Pain:** type **My knee hurts on lunges**. **Expect:** a card **"Stop using all lunges (knee pain) until you allow them again"**. Apply it. On **TRAIN**, no lunge, split squat or step-up appears; where one was, a red line says you're avoiding them. Undo brings them back.
4. **Nothing to change:** ask for something that's already true (e.g. **Switch to light mode** while it's light). **Expect:** "Nothing to change" and no Apply button.
5. **Keyboard:** type a message and tap **Send**, or the keyboard's send key. **Expect:** the keyboard closes.
6. **Smoothness:** switch tabs, open and close a session on TRAIN, and watch an answer appear. **Expect:** short fades and smooth expanding, with no jumps. While an answer streams, scroll up: it shouldn't pull you back down.
7. **TODAY:** the header no longer says "Phase 0"; the unfinished cards say what's coming. A session with only bodyweight exercises has no "pick a weight" note.

### Phase 1c-3 — log a workout (Claude checked 1–6 and 9 on your phone on 2026-10-21; 7 and 8 need a real workout of yours)
Do this on a day TRAIN shows a session (or change a day to free in YOU → Training setup to get one today).
1. **Start:** on **TODAY** (or TRAIN → today's session) tap **Start workout**. **Expect:** a dark screen with "← End", "Exercise 1 of N · 0:0x" counting up, the exercise name, a line like "3 × 8–12 · rest 90 s" and "First time: pick a weight…". The screen stays on.
2. **Log a set:** set the kg with − / + (dumbbells step through *your* listed weights; a barbell steps 2.5 kg) and the reps, then tap the square **Done** box. **Expect:** it fills in red, the row dims, and **Rest** counts down with a thin red bar. **Skip** hides it. Changing set 1's kg moves the later, untouched sets with it.
3. **Effort (optional):** tap 7–10 under the sets. It applies to the set you ticked last.
4. **Next / Prev:** **Next →** moves on; the footer shows what's next. **← Prev** goes back, with your ticks still there.
5. **Close mid-workout:** press Home, wait, and come back (or swipe M³ away and reopen it). On TODAY the button now says **Resume workout**. **Expect:** the same sets are ticked, and you land on the first unfinished exercise.
6. **Finish:** on the last exercise tap **Finish** (or **← End** → **End**). **Expect:** you're back where you started.
7. **Progression:** next time this session comes round (or ask the Coach), the line shows a weight. If every set of an exercise was at the top of the range (e.g. 12 of 8–12), it's one step heavier: your next listed dumbbell, +2.5 kg on a barbell, +5 kg on a machine. If you were below the range twice in a row, it's one step lighter.
8. **Coach:** ask **What weight should I use for <exercise> next time?** **Expect:** the same weight the workout screen shows, never a different number.
9. **Lock:** during a workout, leave M³ for more than 5 minutes and come back. **Expect:** the lock screen. No "End workout?" box shows on top of it, and the phone can turn its screen off while locked.
10. **Broken if:** a ticked set is lost after closing the app, a weight jumps more than one step, the Coach names a different weight, or the app closes by itself.
