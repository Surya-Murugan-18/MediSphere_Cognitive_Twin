export const manifest = {
  screens: {
    scr_4dhcn8: { name: "Login", route: "/", state: { "authenticated": false }, position: { "x": 160, "y": 220 } },
    scr_grri38: { name: "Dashboard", route: "/", state: { "authenticated": true }, position: { "x": 1560, "y": 220 } },
    scr_xtijbj: { name: "Patients", route: "/patients", state: { "authenticated": true }, position: { "x": 160, "y": 2200 } },
    scr_adoib9: { name: "Add Patient", route: "/patients/new", state: { "authenticated": true }, position: { "x": 1560, "y": 2200 } },
    scr_5ze9tl: { name: "Patient 360", route: "/patients/P001", state: { "authenticated": true }, position: { "x": 2960, "y": 2200 } },
    scr_akw9x4: { name: "Digital Health Twin", route: "/twins/P001", state: { "authenticated": true }, position: { "x": 1560, "y": 4180 } },
    scr_o82int: { name: "Vitals", route: "/vitals/P001", state: { "authenticated": true }, position: { "x": 160, "y": 4180 } },
    scr_yzkz1r: { name: "Lab Results", route: "/labs", state: { "authenticated": true }, position: { "x": 160, "y": 6160 } },
    scr_qyzas6: { name: "AI Risk Prediction", route: "/predictions", state: { "authenticated": true }, position: { "x": 160, "y": 8140 } },
    scr_du431g: { name: "Prediction Explainability", route: "/explain", state: { "authenticated": true }, position: { "x": 1560, "y": 8140 } },
    scr_oi68bo: { name: "Federated Learning", route: "/federated", state: { "authenticated": true }, position: { "x": 2960, "y": 8140 } },
    scr_9z870d: { name: "Real-Time Monitoring", route: "/monitoring", state: { "authenticated": true }, position: { "x": 2960, "y": 4180 } },
    scr_2p4urt: { name: "Clinical Alerts", route: "/alerts", state: { "authenticated": true }, position: { "x": 160, "y": 10120 } },
    scr_8fo5l1: { name: "Alert Details", route: "/alerts/A-2291", state: { "authenticated": true }, position: { "x": 1560, "y": 10120 } },
    scr_2flrve: { name: "Care Plans", route: "/care-plans", state: { "authenticated": true }, position: { "x": 160, "y": 12100 } },
    scr_vyw8oc: { name: "AI Care Plan Generator", route: "/care-plans/new", state: { "authenticated": true }, position: { "x": 1560, "y": 12100 } },
    scr_87fr5p: { name: "Care Plan Review", route: "/care-plans/CP-001/review", state: { "authenticated": true }, position: { "x": 2960, "y": 12100 } },
    scr_tpgpyp: { name: "Adherence Tracking", route: "/care-plans/CP-001/adherence", state: { "authenticated": true }, position: { "x": 4360, "y": 12100 } },
    scr_xl13he: { name: "Population Health", route: "/population", state: { "authenticated": true }, position: { "x": 160, "y": 14080 } },
    scr_ywr1nd: { name: "Clinical Reports", route: "/reports", state: { "authenticated": true }, position: { "x": 1560, "y": 14080 } },
    scr_lvsgxv: { name: "Patient Consent", route: "/consent", state: { "authenticated": true }, position: { "x": 160, "y": 16060 } },
    scr_43fgxs: { name: "Audit Logs", route: "/audit", state: { "authenticated": true }, position: { "x": 1560, "y": 16060 } },
    scr_snrqdy: { name: "System Status", route: "/status", state: { "authenticated": true }, position: { "x": 2960, "y": 16060 } },
    scr_tpccko: { name: "Settings", route: "/settings", state: { "authenticated": true }, position: { "x": 4360, "y": 16060 } }
  },
  sections: {
    sec_m89ud4: { name: "Authentication & Dashboard", x: 0, y: 0, width: 2920, height: 1180 },
    sec_m2po4k: { name: "Patient Management", x: 0, y: 1980, width: 4320, height: 1180 },
    sec_51cm34: { name: "Patient Health Data", x: 0, y: 3960, width: 4320, height: 1180 },
    sec_a7630z: { name: "Lab Results", x: 0, y: 5940, width: 1520, height: 1180 },
    sec_s4tp30: { name: "AI Risk Prediction", x: 0, y: 7920, width: 4320, height: 1180 },
    sec_azz4z6: { name: "Clinical Alerts", x: 0, y: 9900, width: 2920, height: 1180 },
    sec_pvl4pf: { name: "Care Plans & Adherence", x: 0, y: 11880, width: 5720, height: 1180 },
    sec_s7renu: { name: "Analytics & Reporting", x: 0, y: 13860, width: 2920, height: 1180 },
    sec_tcf1qx: { name: "Administration & Compliance", x: 0, y: 15840, width: 5720, height: 1180 }
  },
  layers: [
  { kind: "section", id: "sec_m89ud4", children: [
    { kind: "screen", id: "scr_4dhcn8" },
    { kind: "screen", id: "scr_grri38" }]
  },
  { kind: "section", id: "sec_m2po4k", children: [
    { kind: "screen", id: "scr_xtijbj" },
    { kind: "screen", id: "scr_adoib9" },
    { kind: "screen", id: "scr_5ze9tl" }]
  },
  { kind: "section", id: "sec_51cm34", children: [
    { kind: "screen", id: "scr_o82int" },
    { kind: "screen", id: "scr_akw9x4" },
    { kind: "screen", id: "scr_9z870d" }]
  },
  { kind: "section", id: "sec_a7630z", children: [
    { kind: "screen", id: "scr_yzkz1r" }]
  },
  { kind: "section", id: "sec_s4tp30", children: [
    { kind: "screen", id: "scr_qyzas6" },
    { kind: "screen", id: "scr_du431g" },
    { kind: "screen", id: "scr_oi68bo" }]
  },
  { kind: "section", id: "sec_azz4z6", children: [
    { kind: "screen", id: "scr_2p4urt" },
    { kind: "screen", id: "scr_8fo5l1" }]
  },
  { kind: "section", id: "sec_pvl4pf", children: [
    { kind: "screen", id: "scr_2flrve" },
    { kind: "screen", id: "scr_vyw8oc" },
    { kind: "screen", id: "scr_87fr5p" },
    { kind: "screen", id: "scr_tpgpyp" }]
  },
  { kind: "section", id: "sec_s7renu", children: [
    { kind: "screen", id: "scr_xl13he" },
    { kind: "screen", id: "scr_ywr1nd" }]
  },
  { kind: "section", id: "sec_tcf1qx", children: [
    { kind: "screen", id: "scr_lvsgxv" },
    { kind: "screen", id: "scr_43fgxs" },
    { kind: "screen", id: "scr_snrqdy" },
    { kind: "screen", id: "scr_tpccko" }]
  }]

};