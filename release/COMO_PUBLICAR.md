# Cómo publicar CobbleTCG Shop

Todo lo que necesitas está en esta carpeta (`release/`):

- `cobbletcg-shop-0.1.0.jar`: la **versión pública**. No incluye los 14 logros corregidos de ScalperTCG, que son archivos de otro autor. Tu instalación personal sí los tiene.
- `CURSEFORGE.md`: la descripción para la página de CurseForge (en inglés, lista para pegar).
- `../README.md`: la página del repositorio de GitHub, con todos los ajustes explicados.

---

## 1. CurseForge

Crear un proyecto en CurseForge solo se puede hacer desde su web, con tu cuenta. Después, CurseForge lo revisa antes de publicarlo; suele tardar de unas horas a unos días.

1. Entra en <https://authors.curseforge.com> e inicia sesión.
2. **Create Project → Minecraft → Mods**.
3. Rellena los datos:
   - **Name:** `CobbleTCG Shop`
   - **Summary** (frase corta): `Run your own Pokémon TCG card shop: packs, prices, customers, grading and a ranked league.`
   - **Description:** cambia el editor a **Markdown** y pega todo el contenido de `CURSEFORGE.md`.
   - **Categories:** Adventure and RPG, Miscellaneous y Addons (Cobblemon, si aparece). Economy si existe.
   - **License:** All Rights Reserved, que es la del mod, u otra que prefieras.
   - **Logo:** una imagen cuadrada de al menos 400×400 px. Una captura bonita de tu tienda o de una apertura de sobres funciona muy bien.
4. Guarda y ve a **Files → Upload File**:
   - **File:** `release/cobbletcg-shop-0.1.0.jar`
   - **Release type:** Release, o Beta si prefieres avisar de que es una primera versión.
   - **Game versions:** `1.21.1`
   - **Mod loader:** Fabric
   - **Java:** 21
   - **Changelog:** `First public release.`
5. En **Relations / Dependencies** del archivo, añade:
   - **Required Dependency:** ScalperTCG, CobbleDollars, Fabric API
   - **Optional Dependency:** Cobblemon, Pokeblocks
   Con esto, la app de CurseForge instala sola las dependencias obligatorias.
6. Añade algunas **capturas** en la pestaña Images. Ayudan muchísimo:
   - tu tienda llena de clientes;
   - la apertura de sobres;
   - el teléfono;
   - un duelo;
   - el binder.
7. Envía el proyecto a revisión.

**Recomendado:** escríbele a SorEscopeta, el autor de ScalperTCG, para contarle que existe este add-on antes de publicarlo. Es lo correcto, y puede que lo enlace desde su página.

## 2. GitHub

1. Crea un repositorio nuevo en <https://github.com/new>, por ejemplo `cobbletcg-shop`. No marques "Add a README", porque ya tienes uno.
2. En una terminal, dentro de la carpeta del proyecto (`_addon-dev\tcgshop`), ejecuta:

```powershell
git init
git add .
git commit -m "CobbleTCG Shop 0.1.0"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/cobbletcg-shop.git
git push -u origin main
```

El `.gitignore` ya excluye:
- lo compilado y las pruebas;
- `node_modules`;
- los archivos de ScalperTCG (no se suben, porque son de otro autor).

3. En la página del repositorio, crea una **Release** (Releases → Draft a new release) y adjunta el mismo jar.
4. Pega el enlace de GitHub en CurseForge: **Source** y **Issues**, en la configuración del proyecto.
