package xyz.devnerd.anmediaplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Real filenames sampled from DHAKA-FLIX series folders. */
class PrettyNameTest {
    private fun check(file: String, primary: String, secondary: String, label: String = primary) {
        val np = prettyName(file)
        assertEquals("primary: $file", primary, np.primary)
        assertEquals("secondary: $file", secondary, np.secondary)
        assertEquals("label: $file", label, np.playlistLabel)
    }

    @Test fun dottedSeasonEpisode() = check(
        "2.Weeks.S01E03.KOR.720p.NF.WEB-DL.x264.MSubs-Pahe.mkv", "Episode 3", "2 Weeks · S01", "S1 · Episode 3",
    )

    @Test fun spacedSeasonEpisode() = check(
        "18 Again S01E02  720p AMZN WEBRip x265 HEVC ESub [Dual Audio][Hindi 2.0+Korean 2.0] -MsMod.mkv",
        "Episode 2", "18 Again · S01", "S1 · Episode 2",
    )

    @Test fun noSeason() = check(
        "100.Days.My.Prince.E01.KOR.720p.NF.WEBRip.x265.HEVC.10bit.MSubs-MsMod.mkv", "Episode 1", "100 Days My Prince",
    )

    @Test fun bareTrailingNumber() = check("Arang and Magistrate 04.mkv", "Episode 4", "Arang and Magistrate")

    @Test fun episodeTitle() = check(
        "Malcolm.in.the.Middle.S01E02.Red.Dress.720p.NF.WEB-DL.x264-Pahe.in.mkv",
        "Episode 2 · Red Dress", "Malcolm in the Middle · S01", "S1 · Episode 2 · Red Dress",
    )

    @Test fun redundantEpisodeTitleDropped() = check(
        "Beautiful.World.S01E02.Episode.2.720p.MX.WEB-DL.AAC2.0.x264-mkvCinemas.mkv",
        "Episode 2", "Beautiful World · S01", "S1 · Episode 2",
    )

    @Test fun languageTagStopsTitle() = check(
        "20th Century Boy and Girl S01E01 Hindi Dubbed 720p AMZN WEBRip x265 HEVC 10bit ESub-MsMod.mkv",
        "Episode 1", "20th Century Boy and Girl · S01", "S1 · Episode 1",
    )

    @Test fun doubleEpisode() = check(
        "Monk S01E01–E02 Mr. Monk and the Candidate  [1080p x265 10bit Joy].mkv",
        "Episodes 1–2 · Mr Monk and the Candidate", "Monk · S01", "S1 · Episodes 1–2 · Mr Monk and the Candidate",
    )

    @Test fun lowercase() = check("magic.city.s01e02.720p.bluray.x264.350mb-pahe.in.mkv", "Episode 2", "magic city · S01", "S1 · Episode 2")

    @Test fun movieUnchanged() = check("Inception (2010) 1080p.mkv", "Inception", "2010")

    @Test fun plainFileUnchanged() = check("Some.Random.Clip.mp4", "Some Random Clip", "")

    @Test fun sortsBySeasonEpisodeDespiteMixedGroups() {
        val names = listOf(
            "Bargain.S01E10.KOR.720p.WEB-DL.x265.HEVC.10bit.AAC-Phanteam.mkv",
            "Bargain.S01E02.KOR.720p.WEB-DL.x264.ESub-MkvDrama.mkv",
            "Bargain S01E09 720p.mkv",
        )
        val sorted = xyz.devnerd.anmediaplayer.data.source.naturalVideoSort(
            names.map { Entry(name = it, type = EntryType.VIDEO, isDir = false) },
        ).map { prettyName(it.name).ep }
        assertEquals(listOf(2, 9, 10), sorted)
    }
}
