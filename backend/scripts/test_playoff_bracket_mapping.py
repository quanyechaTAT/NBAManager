import sys

import nba_data_fetcher as fetcher


def test_playoff_series_wins_stay_with_team_when_home_court_flips():
    series_data = [
        {
            "seriesId": "0042500101",
            "homeTeamId": 1610612739,
            "awayTeamId": 1610612765,
            "homeTeam": "Cavaliers",
            "awayTeam": "Pistons",
            "round": 1,
        }
    ]

    games_data = [
        {
            "homeTeam": "Cavaliers",
            "awayTeam": "Pistons",
            "homeSeriesWins": 1,
            "awaySeriesWins": 0,
            "homeScore": 120,
            "awayScore": 100,
            "status": "FINISHED",
        },
        {
            "homeTeam": "Pistons",
            "awayTeam": "Cavaliers",
            "homeSeriesWins": 1,
            "awaySeriesWins": 1,
            "homeScore": 110,
            "awayScore": 115,
            "status": "FINISHED",
        },
    ]

    matchup = fetcher.build_bracket(series_data, games_data)[0]

    assert matchup["seriesId"] == "0042500101"
    assert matchup["team1Name"] == "Cavaliers"
    assert matchup["team2Name"] == "Pistons"
    assert matchup["team1Wins"] == 1
    assert matchup["team2Wins"] == 1


if __name__ == "__main__":
    test_playoff_series_wins_stay_with_team_when_home_court_flips()
    print("playoff bracket mapping regression ok")
