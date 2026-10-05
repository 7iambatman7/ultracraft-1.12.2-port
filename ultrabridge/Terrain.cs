// UltraBridge: getting about on Minecraft's blocky ground. ULTRAKILL's levels are ramps and smooth floors; Minecraft's
// are one-block steps, which ULTRAKILL's player and enemies were never made for.
//
// - V1 steps up a block (as Steve steps up a slab) instead of bumping against it, and comes down one without a stutter.
// - Enemies that lost the navmesh are put back on it, and one stuck at a wall on its way hops up onto the ground beyond.

using System.Collections.Generic;
using HarmonyLib;
using UnityEngine;
using UnityEngine.AI;

namespace UltraBridge
{
    public partial class Bridge
    {
        static readonly AccessTools.FieldRef<NewMovement, Vector3> InputDir = AccessTools.FieldRefAccess<NewMovement, Vector3>("inputDir");
        const int EnvironmentMask = 1 << 8;
        float nextStep;

        /// <summary>Every physics step: V1 walking into a rise of up to a block, with room above it, is lifted onto it.</summary>
        void StepUp(NewMovement nm)
        {
            if (!levelPrepared || !v1Landed || nm == null || nm.dead || nm.rb == null || nm.rb.isKinematic || nm.gc == null) return;
            if (!nm.gc.touchingGround || nm.jumping || Time.time < nextStep) return;
            var col = nm.playerCollider;
            if (col == null) return;
            // where V1 is trying to go (pressed against a wall, its velocity that way is already gone)
            var dir = InputDir(nm);
            dir.y = 0f;
            if (dir.sqrMagnitude < 0.01f)
            {
                var v = nm.rb.velocity;
                dir = new Vector3(v.x, 0f, v.z);
                if (dir.sqrMagnitude < 1f) return;
            }
            dir.Normalize();
            var b = col.bounds;
            float feet = b.min.y, radius = b.extents.x, maxStep = K * 1.05f;
            // something solid of Minecraft's right in front, at foot height
            var low = new Vector3(b.center.x, feet + 0.12f, b.center.z);
            if (!Physics.Raycast(low, dir, out var wall, radius + 0.5f, EnvironmentMask, QueryTriggerInteraction.Ignore)) return;
            if (!IsMinecraftTerrain(wall.collider.transform) || Vector3.Dot(wall.normal, -dir) < 0.5f) return;
            // the top of it, a little past its face
            var probe = wall.point + dir * 0.35f;
            probe.y = feet + maxStep + 0.1f;
            if (!Physics.Raycast(probe, Vector3.down, out var top, maxStep + 0.1f, EnvironmentMask, QueryTriggerInteraction.Ignore)) return;
            float rise = top.point.y - feet;
            if (rise < 0.25f || rise > maxStep || top.normal.y < 0.7f) return;
            // room for all of V1 up there
            var lift = Vector3.up * (rise + 0.04f) + dir * 0.25f;
            var c = b.center + lift;
            float r = radius * 0.9f, half = Mathf.Max(0f, b.extents.y - r);
            if (Physics.CheckCapsule(c + Vector3.up * half, c - Vector3.up * half, r, EnvironmentMask, QueryTriggerInteraction.Ignore)) return;
            nm.rb.position += lift;
            var vel = nm.rb.velocity;
            if (vel.y < 0f) nm.rb.velocity = new Vector3(vel.x, 0f, vel.z);
            nextStep = Time.time + 0.08f;
        }

        // ------------------------------------------------------------ enemies

        class Stride { public Vector3 at; public float since; }
        readonly Dictionary<int, Stride> enemyProgress = new Dictionary<int, Stride>();
        float nextUnstick;

        /// <summary>Twice a second: enemies off the navmesh go back on; ones stuck on their way hop over what stops them
        /// (up to two blocks), or round it.</summary>
        void UnstickEnemies()
        {
            if (Time.time < nextUnstick) return;
            nextUnstick = Time.time + 0.5f;
            List<int> gone = null;
            foreach (var kv in ukEnemies)
            {
                var eid = kv.Value;
                if (eid == null || eid.dead)
                {
                    (gone ?? (gone = new List<int>())).Add(kv.Key);
                    continue;
                }
                if (bossEids.Contains(eid)) continue;
                var nma = eid.GetComponent<NavMeshAgent>();
                // knocked about (its body is physics then) or a flier: not walking
                if (nma == null || !nma.enabled || !nma.gameObject.activeInHierarchy) continue;
                var pos = eid.transform.position;
                if (!nma.isOnNavMesh)
                {
                    if (NavMesh.SamplePosition(pos, out var near, K * 3f, nma.areaMask)) nma.Warp(near.position);
                    continue;
                }
                if (!enemyProgress.TryGetValue(kv.Key, out var pr)) enemyProgress[kv.Key] = pr = new Stride { at = pos, since = Time.time };
                bool wantsToGo = nma.hasPath && !nma.isStopped && nma.remainingDistance > K * 2.5f;
                if (!wantsToGo || (pos - pr.at).sqrMagnitude > K * K * 0.25f)
                {
                    pr.at = pos;
                    pr.since = Time.time;
                    continue;
                }
                if (Time.time - pr.since < 2f) continue;
                // stuck for two seconds: up onto the ground ahead (two blocks at most), towards where it's going
                var ahead = nma.steeringTarget - pos;
                ahead.y = 0f;
                if (ahead.sqrMagnitude < 0.01f) ahead = nma.destination - pos;
                ahead.y = 0f;
                if (ahead.sqrMagnitude < 0.01f) continue;
                ahead = ahead.normalized;
                for (int step = 1; step <= 3; step++)
                {
                    var spot = pos + ahead * (K * step) + Vector3.up * (K * 2.2f);
                    if (!NavMesh.SamplePosition(spot, out var hit, K * 1.5f, nma.areaMask)) continue;
                    if (hit.position.y - pos.y > K * 2.3f || (hit.position - pos).sqrMagnitude < K * K * 0.5f) continue;
                    nma.Warp(hit.position);
                    break;
                }
                pr.at = eid.transform.position;
                pr.since = Time.time;
            }
            if (gone != null) foreach (var id in gone) enemyProgress.Remove(id);
        }
    }
}
