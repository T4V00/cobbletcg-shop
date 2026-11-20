// Sustituto vacío de los módulos de Node que el motor no necesita dentro de Minecraft.
module.exports = new Proxy(function () { return module.exports; }, {
  get: (t, k) => (k === '__esModule' ? false : k === 'default' ? module.exports : module.exports),
  apply: () => module.exports,
  construct: () => ({})
});
