// UltraBridge, part four: Ultracraft's settings screen (in Minecraft) acting on ULTRAKILL.
//
// - OPTS: how long impact frames last (0.1x to 3x of ULTRAKILL's own).
// - UKPREF key type:value: one of ULTRAKILL's own settings (mouse sensitivity, field of view, screen shake...) as
//   Ultracraft plays it. It is answered in place of the stored one while ULTRAKILL runs and never written into its
//   settings files, so ULTRAKILL on its own stays as the player set it. UKPREFS reports the stored ones.
// - QUIT: Minecraft closed, and it started this ULTRAKILL.

using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using HarmonyLib;
using UnityEngine;

namespace UltraBridge
{
    public partial class Bridge
    {
        /// <summary>Impact frames (TimeController.HitStop/TrueStop) last this many times as long.</summary>
        public static float ImpactScale = 1f;

        /// <summary>ULTRAKILL settings Ultracraft has set: answered in place of PrefsManager's own.</summary>
        public static readonly Dictionary<string, object> PrefOverrides = new Dictionary<string, object>();
        /// <summary>Set while reading ULTRAKILL's own stored settings (UKPREFS), past the overrides.</summary>
        public static bool ReadingStoredPrefs;

        // the settings Minecraft's screen shows, with their types (f float, i int, b bool; l = a local pref)
        static readonly string[][] ReportedPrefs =
        {
            new[] { "mouseSensitivity", "fl" }, new[] { "fieldOfView", "f" }, new[] { "screenShake", "f" }, new[] { "cameraTilt", "b" },
            new[] { "parryFlash", "b" }, new[] { "weaponHoldPosition", "i" }, new[] { "mouseReverseY", "b" }, new[] { "bloodEnabled", "bl" },
            new[] { "allVolume", "f" }, new[] { "musicVolume", "f" },
        };

        void HandleSettings(string cmd, string rest)
        {
            switch (cmd)
            {
                case "OPTS":
                    // OPTS key=value ...
                    foreach (var kv in rest.Split(' '))
                    {
                        var p = kv.Split('=');
                        if (p.Length != 2) continue;
                        if (p[0] == "impact" && float.TryParse(p[1], NumberStyles.Float, CultureInfo.InvariantCulture, out var f)) ImpactScale = Mathf.Clamp(f, 0.1f, 3f);
                    }
                    break;
                case "UKPREF":
                {
                    // UKPREF key type:value
                    var a = rest.Split(' ');
                    if (a.Length < 2 || a[1].Length < 3 || a[1][1] != ':') break;
                    string key = a[0], v = a[1].Substring(2);
                    object value;
                    switch (a[1][0])
                    {
                        case 'f': value = float.Parse(v, CultureInfo.InvariantCulture); break;
                        case 'i': value = int.Parse(v, CultureInfo.InvariantCulture); break;
                        case 'b': value = v == "true" || v == "1"; break;
                        default: return;
                    }
                    PrefOverrides[key] = value;
                    ApplyPref(key, value);
                    break;
                }
                case "QUIT":
                    Plugin.Log.LogInfo("Minecraft closed: quitting");
                    Application.Quit();
                    break;
                default:
                    HandleMultiplayer(cmd, rest);
                    break;
            }
        }

        /// <summary>Everything listening for that setting hears it changed, as if set in ULTRAKILL's own menu.</summary>
        void ApplyPref(string key, object value)
        {
            if (!levelPrepared) return;
            try
            {
                if (key == "mouseSensitivity" && value is float s)
                {
                    var opm = MonoSingleton<OptionsManager>.Instance;
                    if (opm != null) opm.mouseSensitivity = s;
                }
                PrefsManager.onPrefChanged?.Invoke(key, value);
            }
            catch (Exception e) { Plugin.Log.LogWarning("setting " + key + ": " + e.Message); }
        }

        /// <summary>The level is ready: every setting Ultracraft has made applies (again), and Minecraft hears the
        /// stored ones for its settings screen.</summary>
        void PrefsOnReady()
        {
            foreach (var kv in PrefOverrides) ApplyPref(kv.Key, kv.Value);
            var pm = MonoSingleton<PrefsManager>.Instance;
            if (pm == null) return;
            var sb = new StringBuilder("UKPREFS ");
            ReadingStoredPrefs = true;
            try
            {
                foreach (var p in ReportedPrefs)
                {
                    bool local = p[1].Length > 1;
                    string v;
                    switch (p[1][0])
                    {
                        case 'f': v = "f:" + S(local ? pm.GetFloatLocal(p[0]) : pm.GetFloat(p[0])); break;
                        case 'i': v = "i:" + (local ? pm.GetIntLocal(p[0]) : pm.GetInt(p[0])); break;
                        default: v = "b:" + ((local ? pm.GetBoolLocal(p[0]) : pm.GetBool(p[0])) ? "true" : "false"); break;
                    }
                    sb.Append(p[0]).Append('=').Append(v).Append(';');
                }
            }
            catch (Exception e) { Plugin.Log.LogWarning("UKPREFS: " + e.Message); }
            finally { ReadingStoredPrefs = false; }
            Net.Send(sb.ToString());
        }
    }

    /// <summary>ULTRAKILL asks for a setting: Ultracraft's, if it set one.</summary>
    [HarmonyPatch(typeof(PrefsManager), nameof(PrefsManager.GetFloat))]
    static class PrefFloat
    {
        static void Postfix(string key, ref float __result)
        {
            if (!Bridge.ReadingStoredPrefs && Bridge.PrefOverrides.TryGetValue(key, out var v) && v is float f) __result = f;
        }
    }

    [HarmonyPatch(typeof(PrefsManager), nameof(PrefsManager.GetFloatLocal))]
    static class PrefFloatLocal
    {
        static void Postfix(string key, ref float __result)
        {
            if (!Bridge.ReadingStoredPrefs && Bridge.PrefOverrides.TryGetValue(key, out var v) && v is float f) __result = f;
        }
    }

    [HarmonyPatch(typeof(PrefsManager), nameof(PrefsManager.GetInt))]
    static class PrefInt
    {
        static void Postfix(string key, ref int __result)
        {
            if (!Bridge.ReadingStoredPrefs && Bridge.PrefOverrides.TryGetValue(key, out var v) && v is int i) __result = i;
        }
    }

    [HarmonyPatch(typeof(PrefsManager), nameof(PrefsManager.GetIntLocal))]
    static class PrefIntLocal
    {
        static void Postfix(string key, ref int __result)
        {
            if (!Bridge.ReadingStoredPrefs && Bridge.PrefOverrides.TryGetValue(key, out var v) && v is int i) __result = i;
        }
    }

    [HarmonyPatch(typeof(PrefsManager), nameof(PrefsManager.GetBool))]
    static class PrefBool
    {
        static void Postfix(string key, ref bool __result)
        {
            if (!Bridge.ReadingStoredPrefs && Bridge.PrefOverrides.TryGetValue(key, out var v) && v is bool b) __result = b;
        }
    }

    [HarmonyPatch(typeof(PrefsManager), nameof(PrefsManager.GetBoolLocal))]
    static class PrefBoolLocal
    {
        static void Postfix(string key, ref bool __result)
        {
            if (!Bridge.ReadingStoredPrefs && Bridge.PrefOverrides.TryGetValue(key, out var v) && v is bool b) __result = b;
        }
    }

    /// <summary>Impact frames as long as the Impact Frames setting says (0.1x to 3x).</summary>
    [HarmonyPatch(typeof(TimeController), nameof(TimeController.HitStop))]
    static class HitStopLength
    {
        static void Prefix(ref float length) => length *= Bridge.ImpactScale;
    }

    [HarmonyPatch(typeof(TimeController), nameof(TimeController.TrueStop))]
    static class TrueStopLength
    {
        static void Prefix(ref float length) => length *= Bridge.ImpactScale;
    }
}
