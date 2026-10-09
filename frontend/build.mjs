import { mkdir, copyFile } from 'node:fs/promises';
await mkdir(new URL('./dist/', import.meta.url), { recursive: true });
for (const file of ['index.html', 'styles.css', 'theme.css', 'app.js', 'core.js']) {
  await copyFile(new URL(`./public/${file}`, import.meta.url), new URL(`./dist/${file}`, import.meta.url));
}
console.log('Frontend preparado em dist/');
