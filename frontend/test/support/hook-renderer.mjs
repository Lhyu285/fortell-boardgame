import assert from "node:assert/strict";
import React from "react";

// Small React-18 hook/handler unit harness, not a DOM or a browser emulator.
// SSR parity is checked separately with React's real server renderer.
export function mountHooks(Component, initialProps) {
  const dispatcher = React.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED.ReactCurrentDispatcher;
  const instances = new Map();
  let props = initialProps, tree, active, cursor = 0, dirty = false, effects = [];
  const same = (left, right) => left && right && left.length === right.length && left.every((value, index) => Object.is(value, right[index]));
  function slot(kind, initialize) {
    const index = cursor++;
    if (!active[index]) active[index] = { kind, ...initialize() };
    assert.equal(active[index].kind, kind, "Hook ordering changed");
    return active[index];
  }
  const hooks = {
    useState(initial) {
      const cell = slot("state", () => ({ value: typeof initial === "function" ? initial() : initial }));
      cell.set ??= update => {
        const next = typeof update === "function" ? update(cell.value) : update;
        if (!Object.is(next, cell.value)) { cell.value = next; dirty = true; }
      };
      return [cell.value, cell.set];
    },
    useRef(initial) { return slot("ref", () => ({ value: { current: initial } })).value; },
    useMemo(factory, dependencies) {
      const cell = slot("memo", () => ({}));
      if (!same(cell.dependencies, dependencies)) { cell.value = factory(); cell.dependencies = dependencies; }
      return cell.value;
    },
    useEffect(effect, dependencies) {
      const cell = slot("effect", () => ({}));
      if (!same(cell.dependencies, dependencies)) {
        cell.dependencies = dependencies;
        effects.push(() => { cell.cleanup?.(); cell.cleanup = effect(); });
      }
    }
  };
  function expand(element, location) {
    if (element === null || element === undefined || typeof element === "boolean") return null;
    if (Array.isArray(element)) return element.map((child, index) => expand(child, `${location}/${child?.key ?? index}`));
    if (typeof element !== "object") return element;
    if (element.type === React.Fragment || element.type === React.Suspense) return expand(element.props.children, `${location}/fragment`);
    if (element.type && typeof element.type === "object" && element.type._init) {
      return expand(React.createElement(element.type._init(element.type._payload), element.props), `${location}/lazy`);
    }
    if (typeof element.type === "function") {
      const key = `${location}/${element.type.name}`;
      if (!instances.has(key)) instances.set(key, []);
      const previous = dispatcher.current, previousActive = active, previousCursor = cursor;
      active = instances.get(key); cursor = 0; dispatcher.current = hooks;
      let child;
      try { child = element.type(element.props); }
      finally { dispatcher.current = previous; active = previousActive; cursor = previousCursor; }
      return expand(child, key);
    }
    if (element.ref && typeof element.ref === "object") element.ref.current ??= { scrollLeft: 0, scrollWidth: 480 };
    return { type: element.type, props: element.props, children: expand(element.props.children, `${location}/${element.type}`) };
  }
  function flush() {
    let iterations = 0;
    do {
      assert.ok(iterations++ < 25, "Effects did not settle"); dirty = false; effects = [];
      tree = expand(React.createElement(Component, props), "root");
      for (const effect of effects) effect();
    } while (dirty);
    return tree;
  }
  function all(predicate, value = tree, output = []) {
    if (Array.isArray(value)) value.forEach(child => all(predicate, child, output));
    else if (value && typeof value === "object") {
      if (predicate(value)) output.push(value);
      all(predicate, value.children, output);
    }
    return output;
  }
  flush();
  return {
    all,
    get tree() { return tree; },
    act(callback) { callback(); flush(); },
    update(nextProps) { props = nextProps; flush(); },
    unmount() { for (const cells of instances.values()) for (const cell of cells) cell.cleanup?.(); }
  };
}
export function textOf(node) {
  if (Array.isArray(node)) return node.map(textOf).join("");
  if (node && typeof node === "object") return textOf(node.children);
  return node === null || node === undefined ? "" : String(node);
}
