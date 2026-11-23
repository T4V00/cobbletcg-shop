// Empty stand-in for the Node modules the engine doesn't need inside Minecraft.
module.exports = new Proxy(function () { return module.exports; }, {
  get: (t, k) => (k === '__esModule' ? false : k === 'default' ? module.exports : module.exports),
  apply: () => module.exports,
  construct: () => ({})
});
