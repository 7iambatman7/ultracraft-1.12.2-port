// UltraBridge: ULTRAKILL's music for Minecraft's fights. Minecraft picks a song from ULTRAKILL's soundtrack (MUSIC
// key, random, or off); ULTRAKILL's own music manager plays it the way its levels do: the battle version while enemies
// are on V1 (they ask for it themselves), the song's calm version (or quiet) otherwise, crossfading between them. A boss
// or an arena can bring its own song (THEME key, - when it's over). Minecraft hears when a fight starts and ends
// (FIGHT 1/0), to hush its own music meanwhile.

using System;
using System.Collections.Generic;
using System.Text;
using UnityEngine;
using UnityEngine.AddressableAssets;
using UnityEngine.ResourceManagement.AsyncOperations;

namespace UltraBridge
{
    public partial class Bridge
    {
        const string SoundtrackRoot = "Assets/Data/Soundtrack/";

        /// <summary>Minecraft's choice: "off", "random", or a song's key.</summary>
        string musicChoice = "off";
        /// <summary>Between fights the song's calm version plays (if ULTRAKILL has one); otherwise it's quiet.</summary>
        bool musicCalm;
        /// <summary>A boss's or an arena's own song while it lasts (null: none).</summary>
        string themeSong;
        static List<string> songKeys;
        string songInPlayer, songLoading;
        // the song in the player is a boss's or an arena's (not one Random should keep)
        bool playingTheme;
        AsyncOperationHandle<SoundtrackSong> songHandle;
        AsyncOperationHandle<AudioClip> calmHandle;
        bool fightNow;
        float fightEndedAt = -999f;
        float nextMusicCheck;

        void HandleMusic(string cmd, string rest)
        {
            switch (cmd)
            {
                case "MUSIC":
                    // MUSIC off|random|<song key>: what plays in fights
                    musicChoice = rest.Trim();
                    if (musicChoice.Length == 0) musicChoice = "off";
                    if (levelPrepared) MusicChanged();
                    break;
                case "MUSICOPTS":
                    // MUSICOPTS calm=0/1
                    foreach (var kv in rest.Split(' '))
                        if (kv.StartsWith("calm=")) musicCalm = kv.EndsWith("1");
                    if (levelPrepared && songInPlayer != null) LoadSong(songInPlayer, true);
                    break;
                case "THEME":
                    // THEME <song key>|-: a boss's or an arena's own song, until it's over
                    themeSong = rest.Trim() == "-" || rest.Trim().Length == 0 ? null : rest.Trim();
                    if (levelPrepared) MusicChanged();
                    break;
            }
        }

        /// <summary>Every song ULTRAKILL's soundtrack has (its Cyber Grind's jukebox list), from its asset catalog.</summary>
        static List<string> SongKeys()
        {
            if (songKeys != null) return songKeys;
            var found = new SortedSet<string>(StringComparer.Ordinal);
            try
            {
                foreach (var loc in Addressables.ResourceLocators)
                    foreach (var key in loc.Keys)
                        if (key is string s && s.StartsWith(SoundtrackRoot) && s.EndsWith(".asset")) found.Add(s);
            }
            catch (Exception e) { Plugin.Log.LogWarning("songs: " + e.Message); }
            songKeys = new List<string>(found);
            return songKeys;
        }

        /// <summary>SONGS key;key;...: the soundtrack, for Minecraft's music picker.</summary>
        void SendSongs()
        {
            var sb = new StringBuilder("SONGS ");
            foreach (var k in SongKeys()) sb.Append(k.Substring(SoundtrackRoot.Length)).Append(';');
            Net.Send(sb.ToString());
        }

        /// <summary>Not for a random fight: ULTRAKILL's calm pieces (interludes, secret levels, the museum).</summary>
        static bool Calm(string key) =>
            key.StartsWith("Misc/") || key.StartsWith("Secret Levels/") || key.Contains("Clair de Lune") || key.Contains("Bach BWV") || key.Contains("Beethoven")
            || key.Contains("Weihnachten") || key.Contains("Silence. Introspection") || key.Contains("Disgrace. Humiliation") || key.Contains("An Absence")
            || key.Contains("Aftermath") || key.Contains("Shopping Ver") || key.Contains("The World Looks White");

        /// <summary>The song for the next fight: a theme first, then Minecraft's choice.</summary>
        string WantedSong()
        {
            if (themeSong != null) return themeSong;
            if (musicChoice == "off") return null;
            if (musicChoice == "random")
            {
                var keys = SongKeys().FindAll(k => !Calm(k.Substring(SoundtrackRoot.Length)));
                if (keys.Count == 0) return null;
                // the same song through a fight and a short breather; a new one after a real lull
                if (songInPlayer != null && !playingTheme && Time.time - fightEndedAt < 30f) return songInPlayer;
                return keys[UnityEngine.Random.Range(0, keys.Count)].Substring(SoundtrackRoot.Length);
            }
            return musicChoice;
        }

        void MusicChanged()
        {
            string want = WantedSong();
            playingTheme = themeSong != null;
            if (want == null)
            {
                StopUkMusic();
                return;
            }
            if (want != songInPlayer && want != songLoading) LoadSong(want, false);
        }

        void StopUkMusic()
        {
            songInPlayer = null;
            songLoading = null;
            var mm = MonoSingleton<MusicManager>.Instance;
            if (mm != null)
            {
                try { mm.ForceStopMusic(); }
                catch (Exception e) { Plugin.Log.LogDebug("music stop: " + e.Message); }
            }
        }

        void LoadSong(string key, bool reload)
        {
            string full = SoundtrackRoot + key;
            songLoading = key;
            AsyncOperationHandle<SoundtrackSong> h;
            try { h = Addressables.LoadAssetAsync<SoundtrackSong>(full); }
            catch (Exception e)
            {
                Plugin.Log.LogWarning("music " + key + ": " + e.Message);
                songLoading = null;
                return;
            }
            h.Completed += op =>
            {
                if (songLoading != key)
                {
                    Addressables.Release(op);
                    return;
                }
                songLoading = null;
                if (op.Status != AsyncOperationStatus.Succeeded || op.Result == null)
                {
                    Plugin.Log.LogWarning("music " + key + ": couldn't load");
                    Addressables.Release(op);
                    return;
                }
                if (songHandle.IsValid()) Addressables.Release(songHandle);
                songHandle = op;
                PlaySong(key, op.Result);
            };
        }

        void PlaySong(string key, SoundtrackSong song)
        {
            var mm = MonoSingleton<MusicManager>.Instance;
            AudioClip clip = song.clips != null && song.clips.Count > 0 && song.clips[0] != null ? song.clips[0] : song.introClip;
            if (mm == null || clip == null || mm.battleTheme == null || mm.cleanTheme == null) return;
            songInPlayer = key;
            mm.battleTheme.clip = clip;
            mm.battleTheme.loop = true;
            if (mm.bossTheme != null)
            {
                mm.bossTheme.clip = clip;
                mm.bossTheme.loop = true;
            }
            mm.cleanTheme.clip = null;
            mm.cleanTheme.loop = true;
            if (calmHandle.IsValid()) Addressables.Release(calmHandle);
            calmHandle = default;
            // its calm version: ULTRAKILL keeps one beside most level songs ("1-1" and "1-1 Clean")
            if (musicCalm)
            {
                string calmKey = "Assets/Music/" + clip.name + " Clean.wav";
                if (HasKey(calmKey))
                {
                    try
                    {
                        var ch = Addressables.LoadAssetAsync<AudioClip>(calmKey);
                        ch.Completed += op =>
                        {
                            if (songInPlayer != key || op.Status != AsyncOperationStatus.Succeeded)
                            {
                                Addressables.Release(op);
                                return;
                            }
                            calmHandle = op;
                            mm.cleanTheme.clip = op.Result;
                            RestartMusic(mm);
                        };
                    }
                    catch (Exception e) { Plugin.Log.LogDebug("calm music: " + e.Message); }
                }
            }
            RestartMusic(mm);
            Plugin.Log.LogInfo("music: " + song.songName + " (" + key + ")" + (musicCalm ? ", calm between fights" : ""));
        }

        static bool HasKey(string key)
        {
            try
            {
                foreach (var loc in Addressables.ResourceLocators)
                    if (loc.Locate(key, typeof(AudioClip), out _)) return true;
            }
            catch (Exception) { }
            return false;
        }

        /// <summary>The music manager plays its themes from the top, with the new clips (battle if a fight is on).</summary>
        static void RestartMusic(MusicManager mm)
        {
            try
            {
                mm.off = true;
                mm.ForceStartMusic();
            }
            catch (Exception e) { Plugin.Log.LogDebug("music start: " + e.Message); }
        }

        /// <summary>A few times a second: whether a fight is on (ULTRAKILL's enemies asked for battle music), and the song
        /// for it.</summary>
        void UpdateMusic()
        {
            if (!levelPrepared || Time.unscaledTime < nextMusicCheck) return;
            nextMusicCheck = Time.unscaledTime + 0.25f;
            var mm = MonoSingleton<MusicManager>.Instance;
            if (mm == null) return;
            bool fight;
            try { fight = mm.IsInBattle(); }
            catch (Exception) { fight = false; }
            if (fight == fightNow) return;
            fightNow = fight;
            if (Net.Connected) Net.Send("FIGHT " + (fight ? 1 : 0));
            if (fight) MusicChanged();
            else fightEndedAt = Time.time;
        }

        /// <summary>Debug: MUSICINFO.</summary>
        string MusicInfo()
        {
            var mm = MonoSingleton<MusicManager>.Instance;
            var sb = new StringBuilder("MUSICINFO choice=" + musicChoice + " theme=" + (themeSong ?? "-") + " playing=" + (songInPlayer ?? "-") + " calm=" + musicCalm
                                       + " fight=" + fightNow + " songs=" + SongKeys().Count);
            if (mm != null)
            {
                sb.Append(" off=").Append(mm.off).Append(" forcedOff=").Append(mm.forcedOff).Append(" requested=").Append(mm.requestedThemes)
                  .Append(" battle=").Append(mm.battleTheme != null && mm.battleTheme.clip != null ? mm.battleTheme.clip.name + "@" + mm.battleTheme.volume.ToString("0.00") + (mm.battleTheme.isPlaying ? "+" : "") : "-")
                  .Append(" clean=").Append(mm.cleanTheme != null && mm.cleanTheme.clip != null ? mm.cleanTheme.clip.name + "@" + mm.cleanTheme.volume.ToString("0.00") : "-")
                  .Append(" target=").Append(mm.targetTheme != null ? mm.targetTheme.name : "-");
            }
            else sb.Append(" no music manager");
            return sb.ToString();
        }
    }
}
