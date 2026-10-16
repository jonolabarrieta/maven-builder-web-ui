# Frontend assets

The application serves compiled CSS and versioned JavaScript from its JAR. Running
or packaging it with Maven requires no Node.js, npm or CDN JavaScript downloads.
Google Fonts still uses its existing external stylesheet and `display=swap`.

For interface wording and distinctions between build profiles, POM profiles and
project removal, see [Interface terminology](TERMINOLOGY.md).

## Updating styles

After changing classes in a template, a controller fragment or JavaScript:

```sh
python3 scripts/build-css.py
```

The script uses the official **Tailwind CSS 3.4.17 standalone CLI**. It downloads
the executable for Linux, macOS or Windows x64 into `target/frontend-tools` on its
first run. Python 3 and internet access are needed only for this development step.
Set `TAILWINDCSS_BIN` to an existing 3.4.17 executable to work without downloading.

Commit `src/main/resources/static/css/app.css` together with the source changes.
The input is the Tailwind configuration, `colors.css`, `responsive.css`, and the
complete class names in the sources listed by `tailwind.config.cjs`. Keep dynamic
class names complete so the compiler can discover every state.

To verify that the committed output is current:

```sh
python3 scripts/build-css.py --check
```

## Updating vendor scripts

`vendor-lock.json` records exact npm versions, archive URLs and SHA-512 integrity.
Each dependency's license is kept next to its script in `static/vendor`.

```sh
python3 scripts/sync-vendor.py
```

This restores the locked files; it does not select newer releases. For a deliberate
upgrade, update the version, archive URL, integrity and template URL together,
then verify the browser behavior. No CDN fallback is used at runtime.
