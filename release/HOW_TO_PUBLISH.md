# How to publish CobbleTCG Shop

Everything you need is in this folder (`release/`):

- `cobbletcg-shop-0.1.0.jar`: the **public version**. It doesn't include the 14 fixed ScalperTCG advancements, which are another author's files. Your personal install does have them.
- `CURSEFORGE.md`: the description for the CurseForge page (in English, ready to paste).
- `../README.md`: the GitHub repository page, with every setting explained.

---

## 1. CurseForge

A CurseForge project can only be created from its website, with your account. Afterwards, CurseForge reviews it before publishing it; this usually takes from a few hours to a few days.

1. Go to <https://authors.curseforge.com> and sign in.
2. **Create Project → Minecraft → Mods**.
3. Fill in the details:
   - **Name:** `CobbleTCG Shop`
   - **Summary** (short sentence): `Run your own Pokémon TCG card shop: packs, prices, customers, grading and a ranked league.`
   - **Description:** switch the editor to **Markdown** and paste the whole content of `CURSEFORGE.md`.
   - **Categories:** Adventure and RPG, Miscellaneous and Addons (Cobblemon, if it shows up). Economy if it exists.
   - **License:** All Rights Reserved, which is the mod's, or another one you prefer.
   - **Logo:** a square image of at least 400×400 px. A nice screenshot of your shop or of a pack opening works very well.
4. Save and go to **Files → Upload File**:
   - **File:** `release/cobbletcg-shop-0.1.0.jar`
   - **Release type:** Release, or Beta if you'd rather flag it as a first version.
   - **Game versions:** `1.21.1`
   - **Mod loader:** Fabric
   - **Java:** 21
   - **Changelog:** `First public release.`
5. In the file's **Relations / Dependencies**, add:
   - **Required Dependency:** ScalperTCG, CobbleDollars, Fabric API
   - **Optional Dependency:** Cobblemon, Pokeblocks
   With this, the CurseForge app installs the required dependencies on its own.
6. Add some **screenshots** in the Images tab. They help a lot:
   - your shop full of customers;
   - pack opening;
   - the phone;
   - a duel;
   - the binder.
7. Submit the project for review.

**Recommended:** write to SorEscopeta, the author of ScalperTCG, to tell them this add-on exists before publishing it. It's the right thing to do, and they might link it from their page.

## 2. GitHub

1. Create a new repository at <https://github.com/new>, for example `cobbletcg-shop`. Don't tick "Add a README", because you already have one.
2. In a terminal, inside the project folder (`_addon-dev\tcgshop`), run:

```powershell
git init
git add .
git commit -m "CobbleTCG Shop 0.1.0"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/cobbletcg-shop.git
git push -u origin main
```

The `.gitignore` already excludes:
- build output and tests;
- `node_modules`;
- the ScalperTCG files (they are not uploaded, because they are another author's).

3. On the repository page, create a **Release** (Releases → Draft a new release) and attach the same jar.
4. Paste the GitHub link in CurseForge: **Source** and **Issues**, in the project settings.
