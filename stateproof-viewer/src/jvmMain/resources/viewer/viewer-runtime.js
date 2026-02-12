(function () {
  "use strict";

  const payload = window.STATEPROOF_GRAPH_PAYLOAD;
  const options = window.STATEPROOF_VIEWER_OPTIONS || {};

  if (!payload || !Array.isArray(payload.states) || !Array.isArray(payload.groups)) {
    throw new Error("StateProof viewer payload is missing or invalid.");
  }

  if (typeof window.cytoscape !== "function") {
    throw new Error("Cytoscape failed to load from bundled assets.");
  }

  const container = document.getElementById("sp-canvas");
  const status = document.getElementById("sp-status");
  const toolbar = document.getElementById("sp-toolbar");
  const searchInput = document.getElementById("sp-search");

  if (!container) {
    throw new Error("Viewer container #sp-canvas not found.");
  }

  if (options.includeToolbar === false && toolbar) {
    toolbar.style.display = "none";
  }

  if (options.enableSearch === false && searchInput) {
    searchInput.disabled = true;
    searchInput.placeholder = "Search disabled";
  }

  const groups = (payload.groups || []).slice().sort((a, b) => String(a.id).localeCompare(String(b.id)));
  const states = (payload.states || []).slice().sort((a, b) => String(a.id).localeCompare(String(b.id)));
  const transitions = (payload.transitions || []).slice().sort((a, b) => String(a.id).localeCompare(String(b.id)));
  const overviewEdges = (payload.overviewEdges || []).slice().sort((a, b) => String(a.id).localeCompare(String(b.id)));

  const groupById = new Map(groups.map((g) => [g.id, g]));
  const stateById = new Map(states.map((s) => [s.id, s]));

  const viewState = {
    mode: "overview", // overview | group | all
    selectedGroupId: null,
    focusedStateId: null,
    highlightedTransitionIds: new Set(),
    showEventLabels: options.showEventLabels !== false,
  };

  const cy = window.cytoscape({
    container,
    elements: [],
    style: [
      {
        selector: "node",
        style: {
          "background-color": "#e4ebe1",
          color: "#1c2420",
          label: "data(label)",
          "text-wrap": "wrap",
          "text-max-width": 150,
          "font-size": 12,
          "font-family": "IBM Plex Sans, Source Sans 3, Segoe UI, sans-serif",
          "text-valign": "center",
          "text-halign": "center",
          width: "label",
          height: "label",
          padding: "9px",
          shape: "round-rectangle",
          "border-color": "#8ca08f",
          "border-width": 1,
        },
      },
      {
        selector: 'node[kind = "group"]',
        style: {
          "background-color": "data(color)",
          "border-color": "#516257",
          "border-width": 1.5,
          "font-size": 13,
          "font-weight": 600,
          "text-outline-color": "#ffffff",
          "text-outline-width": 0,
        },
      },
      {
        selector: 'node[kind = "state"]',
        style: {
          shape: "ellipse",
          "background-color": "#ffffff",
          "border-color": "#688172",
          "border-width": 1,
          width: 54,
          height: 54,
          padding: "4px",
        },
      },
      {
        selector: 'node[kind = "state"][isInitial = 1]',
        style: {
          "border-width": 2.4,
          "border-color": "#0f766e",
        },
      },
      {
        selector: 'node[kind = "external"]',
        style: {
          "background-color": "#f5f5f5",
          "border-color": "#9ba7a3",
          "border-style": "dashed",
          color: "#57625d",
          "font-style": "italic",
        },
      },
      {
        selector: "edge",
        style: {
          width: 1.6,
          "line-color": "#86958d",
          "target-arrow-color": "#86958d",
          "target-arrow-shape": "triangle",
          "curve-style": "bezier",
          label: "data(renderLabel)",
          "font-size": 10,
          "text-background-color": "#ffffff",
          "text-background-opacity": 0.84,
          "text-background-padding": "2px",
          "text-margin-y": -6,
        },
      },
      {
        selector: ".sp-dim",
        style: {
          opacity: 0.16,
        },
      },
      {
        selector: ".sp-focus",
        style: {
          opacity: 1,
        },
      },
      {
        selector: ".sp-focus-primary",
        style: {
          "border-width": 3,
          "border-color": "#d95f0e",
        },
      },
      {
        selector: ".sp-match",
        style: {
          width: 2.8,
          "line-color": "#0f766e",
          "target-arrow-color": "#0f766e",
          opacity: 1,
        },
      },
    ],
    layout: {
      name: "breadthfirst",
      directed: true,
      padding: 24,
      spacingFactor: 1.1,
    },
  });

  wireToolbar();
  wireGraphInteractions();
  render();

  function wireToolbar() {
    bindClick("sp-zoom-in", function () {
      cy.zoom(cy.zoom() * 1.15);
      setStatus(currentStatusLine());
    });

    bindClick("sp-zoom-out", function () {
      cy.zoom(cy.zoom() / 1.15);
      setStatus(currentStatusLine());
    });

    bindClick("sp-fit", function () {
      cy.fit(undefined, 32);
      setStatus(currentStatusLine());
    });

    bindClick("sp-overview", function () {
      viewState.mode = "overview";
      viewState.selectedGroupId = null;
      viewState.focusedStateId = null;
      viewState.highlightedTransitionIds.clear();
      render();
    });

    bindClick("sp-expand-all", function () {
      viewState.mode = "all";
      viewState.focusedStateId = null;
      viewState.highlightedTransitionIds.clear();
      render();
    });

    bindClick("sp-collapse-all", function () {
      viewState.mode = "overview";
      viewState.selectedGroupId = null;
      viewState.focusedStateId = null;
      viewState.highlightedTransitionIds.clear();
      render();
    });

    bindClick("sp-clear-focus", function () {
      viewState.focusedStateId = null;
      viewState.highlightedTransitionIds.clear();
      applyFocusAndHighlight();
      setStatus(currentStatusLine());
    });

    bindClick("sp-toggle-labels", function () {
      viewState.showEventLabels = !viewState.showEventLabels;
      render();
    });

    if (searchInput) {
      searchInput.addEventListener("keydown", function (event) {
        if (event.key === "Enter") {
          runSearch(searchInput.value);
        }
      });

      searchInput.addEventListener("input", function () {
        if (!searchInput.value.trim()) {
          viewState.highlightedTransitionIds.clear();
          viewState.focusedStateId = null;
          applyFocusAndHighlight();
          setStatus(currentStatusLine());
        }
      });
    }
  }

  function wireGraphInteractions() {
    cy.on("tap", "node", function (event) {
      const node = event.target;
      const kind = node.data("kind");

      if (kind === "group") {
        const groupId = node.data("groupId");
        if (groupId) {
          viewState.mode = "group";
          viewState.selectedGroupId = groupId;
          viewState.focusedStateId = null;
          viewState.highlightedTransitionIds.clear();
          render();
        }
        return;
      }

      if (kind === "state") {
        if (options.enableFocusMode === false) {
          return;
        }
        const stateId = node.data("stateId");
        if (stateId) {
          viewState.focusedStateId = stateId;
          viewState.highlightedTransitionIds.clear();
          applyFocusAndHighlight();
          const state = stateById.get(stateId);
          setStatus("Focus: " + (state ? state.displayName : stateId));
        }
      }
    });

    cy.on("tap", function (event) {
      if (event.target === cy && viewState.focusedStateId) {
        viewState.focusedStateId = null;
        applyFocusAndHighlight();
        setStatus(currentStatusLine());
      }
    });
  }

  function runSearch(queryValue) {
    if (options.enableSearch === false) {
      return;
    }

    const query = String(queryValue || "").trim().toLowerCase();
    if (!query) {
      viewState.highlightedTransitionIds.clear();
      viewState.focusedStateId = null;
      applyFocusAndHighlight();
      setStatus(currentStatusLine());
      return;
    }

    const stateMatch = states.find(function (state) {
      return includesQuery(state.displayName, query) || includesQuery(state.id, query) || includesQuery(state.qualifiedName, query);
    });

    if (stateMatch) {
      viewState.mode = "group";
      viewState.selectedGroupId = stateMatch.groupId;
      viewState.focusedStateId = stateMatch.id;
      viewState.highlightedTransitionIds.clear();
      render();
      setStatus("Focus: " + stateMatch.displayName + " (search)");
      return;
    }

    const edgeMatches = transitions.filter(function (transition) {
      return includesQuery(transition.eventName, query) || includesQuery(transition.label, query);
    });

    if (edgeMatches.length > 0) {
      const first = edgeMatches[0];
      const fromState = stateById.get(first.fromStateId);

      if (fromState) {
        viewState.mode = "group";
        viewState.selectedGroupId = fromState.groupId;
      }

      viewState.focusedStateId = null;
      viewState.highlightedTransitionIds = new Set(edgeMatches.map(function (edge) {
        return edge.id;
      }));
      render();
      setStatus("Matched transitions: " + edgeMatches.length + " for \"" + query + "\"");
      return;
    }

    setStatus("No match for \"" + query + "\"");
  }

  function render() {
    const elements = buildElements();

    cy.startBatch();
    cy.elements().remove();
    cy.add(elements);
    cy.endBatch();

    applyRenderedLabels();

    cy.layout({
      name: options.layout === "breadthfirst" ? "breadthfirst" : "breadthfirst",
      directed: true,
      fit: true,
      padding: 30,
      spacingFactor: viewState.mode === "overview" ? 1.4 : 1.1,
    }).run();

    cy.fit(undefined, 34);
    applyFocusAndHighlight();
    setStatus(currentStatusLine());
  }

  function buildElements() {
    if (viewState.mode === "group" && viewState.selectedGroupId && groupById.has(viewState.selectedGroupId)) {
      return buildGroupElements(viewState.selectedGroupId);
    }

    if (viewState.mode === "all") {
      return buildAllExpandedElements();
    }

    return buildOverviewElements();
  }

  function buildOverviewElements() {
    const elements = [];

    groups.forEach(function (group) {
      elements.push({
        data: {
          id: idForGroup(group.id),
          kind: "group",
          groupId: group.id,
          label: group.displayName + (group.stateIds.length > 0 ? " (" + group.stateIds.length + ")" : ""),
          color: colorForGroup(group.id),
        },
      });
    });

    overviewEdges.forEach(function (edge) {
      elements.push({
        data: {
          id: "ov_" + edge.id,
          source: idForGroup(edge.fromGroupId),
          target: idForGroup(edge.toGroupId),
          kind: "edge",
          transitionId: edge.id,
          label: edge.label,
          compactLabel: edge.count + "x",
        },
      });
    });

    return elements;
  }

  function buildAllExpandedElements() {
    const elements = [];
    const externalNodes = new Map();

    groups.forEach(function (group) {
      elements.push({
        data: {
          id: idForGroup(group.id),
          kind: "group",
          groupId: group.id,
          label: group.displayName,
          color: colorForGroup(group.id),
          parent: group.parentGroupId ? idForGroup(group.parentGroupId) : undefined,
        },
      });
    });

    states.forEach(function (state) {
      elements.push({
        data: {
          id: idForState(state.id),
          kind: "state",
          stateId: state.id,
          groupId: state.groupId,
          parent: idForGroup(state.groupId),
          label: state.displayName,
          isInitial: state.isInitial ? 1 : 0,
        },
      });
    });

    transitions.forEach(function (transition) {
      const sourceState = stateById.get(transition.fromStateId);
      if (!sourceState) {
        return;
      }

      const source = idForState(sourceState.id);
      let target = null;

      if (transition.toStateId && stateById.has(transition.toStateId)) {
        target = idForState(transition.toStateId);
      } else {
        const externalKey = transition.toStateDisplayName || "?";
        target = idForExternal(externalKey);
        if (!externalNodes.has(target)) {
          externalNodes.set(target, {
            data: {
              id: target,
              kind: "external",
              label: "External::" + (externalKey || "?"),
            },
          });
        }
      }

      elements.push({
        data: {
          id: "tr_" + transition.id,
          source: source,
          target: target,
          kind: "edge",
          transitionId: transition.id,
          label: transition.label,
          compactLabel: transition.eventName,
        },
      });
    });

    externalNodes.forEach(function (node) {
      elements.push(node);
    });

    return elements;
  }

  function buildGroupElements(groupId) {
    const elements = [];
    const externalNodes = new Map();

    const selectedStates = states.filter(function (state) {
      return state.groupId === groupId;
    });
    const selectedStateIds = new Set(selectedStates.map(function (state) { return state.id; }));

    groups.forEach(function (group) {
      if (group.id === groupId) {
        return;
      }
      elements.push({
        data: {
          id: idForGroup(group.id),
          kind: "group",
          groupId: group.id,
          label: group.displayName + (group.stateIds.length > 0 ? " (" + group.stateIds.length + ")" : ""),
          color: colorForGroup(group.id),
        },
      });
    });

    selectedStates.forEach(function (state) {
      elements.push({
        data: {
          id: idForState(state.id),
          kind: "state",
          stateId: state.id,
          groupId: state.groupId,
          label: state.displayName,
          isInitial: state.isInitial ? 1 : 0,
        },
      });
    });

    transitions.forEach(function (transition) {
      const fromState = stateById.get(transition.fromStateId);
      if (!fromState) {
        return;
      }

      const toState = transition.toStateId ? stateById.get(transition.toStateId) : null;
      const fromInSelected = selectedStateIds.has(fromState.id);
      const toInSelected = toState ? selectedStateIds.has(toState.id) : false;

      if (!fromInSelected && !toInSelected) {
        return;
      }

      let sourceId;
      let targetId;

      if (fromInSelected) {
        sourceId = idForState(fromState.id);
      } else {
        const fromGroupId = fromState.groupId;
        sourceId = idForGroup(fromGroupId);
        if (!groupById.has(fromGroupId)) {
          sourceId = addExternalNode(externalNodes, "Source:" + fromGroupId, "External::" + fromGroupId);
        }
      }

      if (toInSelected && toState) {
        targetId = idForState(toState.id);
      } else if (toState) {
        const toGroupId = toState.groupId;
        targetId = idForGroup(toGroupId);
        if (!groupById.has(toGroupId) || toGroupId === groupId) {
          targetId = addExternalNode(externalNodes, "Target:" + toGroupId, "External::" + toGroupId);
        }
      } else {
        targetId = addExternalNode(externalNodes, "Target:?", "External::?");
      }

      elements.push({
        data: {
          id: "gr_" + transition.id,
          source: sourceId,
          target: targetId,
          kind: "edge",
          transitionId: transition.id,
          label: transition.label,
          compactLabel: transition.eventName,
        },
      });
    });

    externalNodes.forEach(function (node) {
      elements.push(node);
    });

    return elements;
  }

  function addExternalNode(map, key, label) {
    const id = idForExternal(key);
    if (!map.has(id)) {
      map.set(id, {
        data: {
          id: id,
          kind: "external",
          label: label,
        },
      });
    }
    return id;
  }

  function applyRenderedLabels() {
    cy.edges().forEach(function (edge) {
      const label = edge.data("label") || "";
      const compact = edge.data("compactLabel") || "";

      if (viewState.showEventLabels) {
        edge.data("renderLabel", label);
      } else {
        edge.data("renderLabel", viewState.mode === "overview" ? compact : "");
      }
    });
  }

  function applyFocusAndHighlight() {
    cy.elements().removeClass("sp-dim sp-focus sp-focus-primary sp-match");

    if (viewState.focusedStateId) {
      const focusNode = cy.getElementById(idForState(viewState.focusedStateId));
      if (focusNode && focusNode.length > 0) {
        const neighborhood = focusNode.closedNeighborhood();
        cy.elements().addClass("sp-dim");
        neighborhood.removeClass("sp-dim").addClass("sp-focus");
        focusNode.addClass("sp-focus-primary");
      }
    }

    if (viewState.highlightedTransitionIds.size > 0) {
      cy.edges().forEach(function (edge) {
        const transitionId = edge.data("transitionId");
        if (transitionId && viewState.highlightedTransitionIds.has(transitionId)) {
          edge.addClass("sp-match");
          edge.connectedNodes().removeClass("sp-dim");
        } else {
          edge.addClass("sp-dim");
        }
      });
    }
  }

  function currentStatusLine() {
    if (viewState.mode === "group" && viewState.selectedGroupId) {
      const group = groupById.get(viewState.selectedGroupId);
      const groupName = group ? group.displayName : viewState.selectedGroupId;
      return "Mode: Group - " + groupName;
    }

    if (viewState.mode === "all") {
      return "Mode: Expanded";
    }

    return "Mode: Overview";
  }

  function setStatus(text) {
    if (!status) {
      return;
    }
    status.textContent = text + " | Labels: " + (viewState.showEventLabels ? "on" : "off");
  }

  function bindClick(id, handler) {
    const element = document.getElementById(id);
    if (!element) {
      return;
    }
    element.addEventListener("click", handler);
  }

  function colorForGroup(groupId) {
    if (!groupId) {
      return "#e3e8e3";
    }

    if (groupId === "group:General") {
      return "#ececec";
    }

    const palette = [
      "#d8e8ff",
      "#d9f2e6",
      "#fde7d4",
      "#fce5f6",
      "#eae4ff",
      "#f9e4e4",
      "#dff4f8",
      "#fff2cc",
    ];
    const idx = Math.abs(stableHash(groupId)) % palette.length;
    return palette[idx];
  }

  function stableHash(value) {
    let h = 0;
    for (let i = 0; i < value.length; i += 1) {
      h = (Math.imul(31, h) + value.charCodeAt(i)) | 0;
    }
    return h;
  }

  function idForGroup(groupId) {
    return "g_" + sanitizedKey(groupId);
  }

  function idForState(stateId) {
    return "s_" + sanitizedKey(stateId);
  }

  function idForExternal(label) {
    return "x_" + sanitizedKey(label);
  }

  function sanitizedKey(value) {
    const raw = String(value || "");
    const compact = raw.replace(/[^a-zA-Z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 40) || "node";
    const hash = Math.abs(stableHash(raw)).toString(16);
    return compact + "_" + hash;
  }

  function includesQuery(value, query) {
    if (value === null || value === undefined) {
      return false;
    }
    return String(value).toLowerCase().includes(query);
  }
})();
