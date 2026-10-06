// UltraBridge: the Performance page's effect settings (OPTS fx= gore= stains=). Effects Quality uses ULTRAKILL's own
// switches for its effects (simple explosions, fire and spawns, hit sparks, environment particles, how much gore stays at
// once), set for this session only like every other ULTRAKILL setting Ultracraft changes; Low also drops shadows.

using System.Collections.Generic;
using UnityEngine;

namespace UltraBridge
{
    public partial class Bridge
    {
        /// <summary>Ultracraft's extra blood on deaths (bursts and sprays on top of ULTRAKILL's own).</summary>
        public static bool ExtraGore = true;
        /// <summary>Minecraft keeps blood stains on its blocks (Performance → Blood Stains not Off).</summary>
        public static bool StainsOn = true;
        int effectsLevel = 2;
        ShadowQuality? shadowsWere;

        // what each level sets (High: nothing, ULTRAKILL as it is)
        static readonly Dictionary<string, object> MediumFx = new Dictionary<string, object>
        {
            { "simpleExplosions", true }, { "simpleFire", true }, { "disableEnvironmentParticles", true }, { "maxGore", 1500f },
        };
        static readonly Dictionary<string, object> LowFx = new Dictionary<string, object>
        {
            { "simpleExplosions", true }, { "simpleFire", true }, { "disableEnvironmentParticles", true }, { "maxGore", 500f },
            { "simpleSpawns", true }, { "disableHitParticles", true },
        };

        /// <summary>OPTS fx=0|1|2 (Low, Medium, High).</summary>
        void SetEffects(int level)
        {
            level = Mathf.Clamp(level, 0, 2);
            if (level == effectsLevel) return;
            effectsLevel = level;
            var want = level == 0 ? LowFx : level == 1 ? MediumFx : null;
            foreach (var key in LowFx.Keys)
            {
                if (want != null && want.TryGetValue(key, out var v)) SetPref(key, v);
                else ClearPref(key);
            }
            // shadows: the sun's, through every leaf and enemy
            if (level == 0)
            {
                if (shadowsWere == null) shadowsWere = QualitySettings.shadows;
                QualitySettings.shadows = ShadowQuality.Disable;
            }
            else if (shadowsWere != null)
            {
                QualitySettings.shadows = shadowsWere.Value;
                shadowsWere = null;
            }
            Plugin.Log.LogInfo("effects quality " + (level == 0 ? "Low" : level == 1 ? "Medium" : "High"));
        }

        /// <summary>OPTS stains=0|1: with Minecraft keeping none, ULTRAKILL doesn't paint any either.</summary>
        void SetStains(bool on)
        {
            if (on == StainsOn) return;
            StainsOn = on;
            if (on) ClearPref("bloodStainChance");
            else SetPref("bloodStainChance", 0f);
        }

        void SetPref(string key, object value)
        {
            PrefOverrides[key] = value;
            ApplyPref(key, value);
        }

        /// <summary>Back to ULTRAKILL's own stored value (the player's ULTRAKILL setting).</summary>
        void ClearPref(string key)
        {
            if (!PrefOverrides.Remove(key)) return;
            var pm = MonoSingleton<PrefsManager>.Instance;
            if (pm == null) return;
            object stored;
            ReadingStoredPrefs = true;
            try
            {
                stored = key == "maxGore" ? pm.GetFloatLocal(key, 3000f) : key == "bloodStainChance" ? pm.GetFloatLocal(key, 0.5f) : (object)pm.GetBoolLocal(key);
            }
            finally { ReadingStoredPrefs = false; }
            ApplyPref(key, stored);
        }
    }
}
