class CricketRuleItem {
  final String label;
  final String value;

  const CricketRuleItem({
    required this.label,
    required this.value,
  });
}

class CricketRuleCategory {
  final String title;
  final String? subtitle;
  final List<CricketRuleItem> items;

  const CricketRuleCategory({
    required this.title,
    this.subtitle,
    required this.items,
  });
}

class CricketRuleSet {
  final String key;
  final String label;
  final List<CricketRuleItem> highlights;
  final List<CricketRuleCategory> categories;
  final List<String> notes;

  const CricketRuleSet({
    required this.key,
    required this.label,
    required this.highlights,
    required this.categories,
    required this.notes,
  });
}

const List<CricketRuleSet> allCricketRuleSets = <CricketRuleSet>[
  CricketRuleSet(
    key: 't20',
    label: 'T20',
    highlights: <CricketRuleItem>[
      CricketRuleItem(label: 'Wicket', value: '+30 pts'),
      CricketRuleItem(label: 'Run', value: '+1 pts'),
      CricketRuleItem(label: 'Dot Ball', value: '+1 pts'),
    ],
    categories: <CricketRuleCategory>[
      CricketRuleCategory(
        title: 'Batting',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Run', value: '+1'),
          CricketRuleItem(label: 'Boundary Bonus', value: '+4'),
          CricketRuleItem(label: 'Six Bonus', value: '+6'),
          CricketRuleItem(label: '25 Run Bonus', value: '+4'),
          CricketRuleItem(label: '50 Run Bonus', value: '+8'),
          CricketRuleItem(label: '75 Run Bonus', value: '+12'),
          CricketRuleItem(label: '100 Run Bonus', value: '+16'),
          CricketRuleItem(label: 'Duck', value: '-2'),
        ],
      ),
      CricketRuleCategory(
        title: 'Bowling',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Dot Ball', value: '+1'),
          CricketRuleItem(label: 'Wicket', value: '+30'),
          CricketRuleItem(label: 'LBW / Bowled Bonus', value: '+8'),
          CricketRuleItem(label: '3 Wicket Bonus', value: '+4'),
          CricketRuleItem(label: '4 Wicket Bonus', value: '+8'),
          CricketRuleItem(label: '5 Wicket Bonus', value: '+12'),
          CricketRuleItem(label: 'Maiden Over', value: '+12'),
        ],
      ),
      CricketRuleCategory(
        title: 'Fielding',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Catch', value: '+8'),
          CricketRuleItem(label: '3 Catch Bonus', value: '+4'),
          CricketRuleItem(label: 'Stumping', value: '+12'),
          CricketRuleItem(label: 'Run Out Direct Hit', value: '+12'),
          CricketRuleItem(label: 'Run Out Assist', value: '+6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Other',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Captain', value: '2x'),
          CricketRuleItem(label: 'Vice Captain', value: '1.5x'),
          CricketRuleItem(label: 'In Announced Lineups', value: '+4'),
          CricketRuleItem(label: 'Playing Substitute', value: '+4'),
        ],
      ),
      CricketRuleCategory(
        title: 'Economy Rate',
        subtitle: 'Min 2 overs',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Below 5 runs per over', value: '+6'),
          CricketRuleItem(label: '5 - 5.99 runs per over', value: '+4'),
          CricketRuleItem(label: '6 - 7 runs per over', value: '+2'),
          CricketRuleItem(label: '10 - 11 runs per over', value: '-2'),
          CricketRuleItem(label: '11.01 - 12 runs per over', value: '-4'),
          CricketRuleItem(label: 'Above 12 runs per over', value: '-6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Strike Rate',
        subtitle: 'Except bowlers, min 10 balls',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Above 170', value: '+6'),
          CricketRuleItem(label: '150.01 - 170', value: '+4'),
          CricketRuleItem(label: '130 - 150', value: '+2'),
          CricketRuleItem(label: '60 - 70', value: '-2'),
          CricketRuleItem(label: '50 - 59.99', value: '-4'),
          CricketRuleItem(label: 'Below 50', value: '-6'),
        ],
      ),
    ],
    notes: <String>[
      'Century gets only the highest batting bonus.',
      'Low strike-rate negatives apply only at 70 or below.',
    ],
  ),
  CricketRuleSet(
    key: 'odi',
    label: 'OD',
    highlights: <CricketRuleItem>[
      CricketRuleItem(label: 'Wicket', value: '+30 pts'),
      CricketRuleItem(label: 'Run', value: '+1 pts'),
      CricketRuleItem(label: '3 Dot Balls', value: '+1 pts'),
    ],
    categories: <CricketRuleCategory>[
      CricketRuleCategory(
        title: 'Batting',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Run', value: '+1'),
          CricketRuleItem(label: 'Boundary Bonus', value: '+4'),
          CricketRuleItem(label: 'Six Bonus', value: '+6'),
          CricketRuleItem(label: '25 Run Bonus', value: '+4'),
          CricketRuleItem(label: '50 Run Bonus', value: '+8'),
          CricketRuleItem(label: '75 Run Bonus', value: '+12'),
          CricketRuleItem(label: '100 Run Bonus', value: '+16'),
          CricketRuleItem(label: '125 Run Bonus', value: '+20'),
          CricketRuleItem(label: '150 Run Bonus', value: '+24'),
          CricketRuleItem(label: 'Duck', value: '-3'),
        ],
      ),
      CricketRuleCategory(
        title: 'Bowling',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Every 3 Dot Balls', value: '+1'),
          CricketRuleItem(label: 'Wicket', value: '+30'),
          CricketRuleItem(label: 'LBW / Bowled Bonus', value: '+8'),
          CricketRuleItem(label: '4 Wicket Bonus', value: '+4'),
          CricketRuleItem(label: '5 Wicket Bonus', value: '+8'),
          CricketRuleItem(label: '6 Wicket Bonus', value: '+12'),
          CricketRuleItem(label: 'Maiden Over', value: '+4'),
        ],
      ),
      CricketRuleCategory(
        title: 'Fielding',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Catch', value: '+8'),
          CricketRuleItem(label: '3 Catch Bonus', value: '+4'),
          CricketRuleItem(label: 'Stumping', value: '+12'),
          CricketRuleItem(label: 'Run Out Direct Hit', value: '+12'),
          CricketRuleItem(label: 'Run Out Assist', value: '+6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Other',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Captain', value: '2x'),
          CricketRuleItem(label: 'Vice Captain', value: '1.5x'),
          CricketRuleItem(label: 'In Announced Lineups', value: '+4'),
          CricketRuleItem(label: 'Playing Substitute', value: '+4'),
        ],
      ),
      CricketRuleCategory(
        title: 'Economy Rate',
        subtitle: 'Min 5 overs',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Below 2.5 runs per over', value: '+6'),
          CricketRuleItem(label: '2.5 - 3.49 runs per over', value: '+4'),
          CricketRuleItem(label: '3.5 - 4.5 runs per over', value: '+2'),
          CricketRuleItem(label: '7 - 8 runs per over', value: '-2'),
          CricketRuleItem(label: '8.01 - 9 runs per over', value: '-4'),
          CricketRuleItem(label: 'Above 9 runs per over', value: '-6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Strike Rate',
        subtitle: 'Except bowlers, min 20 balls',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Above 140', value: '+6'),
          CricketRuleItem(label: '120.01 - 140', value: '+4'),
          CricketRuleItem(label: '100 - 120', value: '+2'),
          CricketRuleItem(label: '40 - 50', value: '-2'),
          CricketRuleItem(label: '30 - 39.99', value: '-4'),
          CricketRuleItem(label: 'Below 30', value: '-6'),
        ],
      ),
    ],
    notes: <String>[
      'Century gets only the highest batting bonus.',
      'Low strike-rate negatives apply only at 50 or below.',
    ],
  ),
  CricketRuleSet(
    key: 'test',
    label: 'Test',
    highlights: <CricketRuleItem>[
      CricketRuleItem(label: 'Wicket', value: '+20 pts'),
      CricketRuleItem(label: 'Run', value: '+1 pts'),
      CricketRuleItem(label: '150 Run Bonus', value: '+24 pts'),
    ],
    categories: <CricketRuleCategory>[
      CricketRuleCategory(
        title: 'Batting',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Run', value: '+1'),
          CricketRuleItem(label: 'Boundary Bonus', value: '+4'),
          CricketRuleItem(label: 'Six Bonus', value: '+6'),
          CricketRuleItem(label: '25 Run Bonus', value: '+4'),
          CricketRuleItem(label: '50 Run Bonus', value: '+8'),
          CricketRuleItem(label: '75 Run Bonus', value: '+12'),
          CricketRuleItem(label: '100 Run Bonus', value: '+16'),
          CricketRuleItem(label: '125 Run Bonus', value: '+20'),
          CricketRuleItem(label: '150 Run Bonus', value: '+24'),
          CricketRuleItem(label: 'Duck', value: '-4'),
        ],
      ),
      CricketRuleCategory(
        title: 'Bowling',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Wicket', value: '+20'),
          CricketRuleItem(label: 'LBW / Bowled Bonus', value: '+8'),
          CricketRuleItem(label: '4 Wicket Bonus', value: '+4'),
          CricketRuleItem(label: '5 Wicket Bonus', value: '+8'),
          CricketRuleItem(label: '6 Wicket Bonus', value: '+12'),
        ],
      ),
      CricketRuleCategory(
        title: 'Fielding',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Catch', value: '+8'),
          CricketRuleItem(label: 'Stumping', value: '+12'),
          CricketRuleItem(label: 'Run Out Direct Hit', value: '+12'),
          CricketRuleItem(label: 'Run Out Assist', value: '+6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Other',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Captain', value: '2x'),
          CricketRuleItem(label: 'Vice Captain', value: '1.5x'),
          CricketRuleItem(label: 'In Announced Lineups', value: '+4'),
          CricketRuleItem(label: 'Playing Substitute', value: '+4'),
        ],
      ),
    ],
    notes: <String>[
      'Four innings count in Tests.',
      'No strike-rate or economy modifiers in this ruleset.',
    ],
  ),
  CricketRuleSet(
    key: 't10',
    label: 'T10',
    highlights: <CricketRuleItem>[
      CricketRuleItem(label: 'Wicket', value: '+30 pts'),
      CricketRuleItem(label: 'Run', value: '+1 pts'),
      CricketRuleItem(label: 'Dot Ball', value: '+1 pts'),
    ],
    categories: <CricketRuleCategory>[
      CricketRuleCategory(
        title: 'Batting',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Run', value: '+1'),
          CricketRuleItem(label: 'Boundary Bonus', value: '+4'),
          CricketRuleItem(label: 'Six Bonus', value: '+6'),
          CricketRuleItem(label: '25 Run Bonus', value: '+8'),
          CricketRuleItem(label: '50 Run Bonus', value: '+12'),
          CricketRuleItem(label: '75 Run Bonus', value: '+16'),
          CricketRuleItem(label: 'Duck', value: '-2'),
        ],
      ),
      CricketRuleCategory(
        title: 'Bowling',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Dot Ball', value: '+1'),
          CricketRuleItem(label: 'Wicket', value: '+30'),
          CricketRuleItem(label: 'LBW / Bowled Bonus', value: '+8'),
          CricketRuleItem(label: '2 Wicket Bonus', value: '+4'),
          CricketRuleItem(label: '3 Wicket Bonus', value: '+8'),
          CricketRuleItem(label: '4 Wicket Bonus', value: '+12'),
          CricketRuleItem(label: '5 Wicket Bonus', value: '+16'),
          CricketRuleItem(label: 'Maiden Over', value: '+16'),
        ],
      ),
      CricketRuleCategory(
        title: 'Fielding',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Catch', value: '+8'),
          CricketRuleItem(label: '3 Catch Bonus', value: '+4'),
          CricketRuleItem(label: 'Stumping', value: '+12'),
          CricketRuleItem(label: 'Run Out Direct Hit', value: '+12'),
          CricketRuleItem(label: 'Run Out Assist', value: '+6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Other',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Captain', value: '2x'),
          CricketRuleItem(label: 'Vice Captain', value: '1.5x'),
          CricketRuleItem(label: 'In Announced Lineups', value: '+4'),
          CricketRuleItem(label: 'Playing Substitute', value: '+4'),
        ],
      ),
      CricketRuleCategory(
        title: 'Economy Rate',
        subtitle: 'Min 1 over',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Below 7 runs per over', value: '+6'),
          CricketRuleItem(label: '7 - 7.99 runs per over', value: '+4'),
          CricketRuleItem(label: '8 - 9 runs per over', value: '+2'),
          CricketRuleItem(label: '14 - 15 runs per over', value: '-2'),
          CricketRuleItem(label: '15.01 - 16 runs per over', value: '-4'),
          CricketRuleItem(label: 'Above 16 runs per over', value: '-6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Strike Rate',
        subtitle: 'Except bowlers, min 5 balls',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Above 190', value: '+6'),
          CricketRuleItem(label: '170.01 - 190', value: '+4'),
          CricketRuleItem(label: '150 - 170', value: '+2'),
          CricketRuleItem(label: '70 - 80', value: '-2'),
          CricketRuleItem(label: '60 - 69.99', value: '-4'),
          CricketRuleItem(label: 'Below 60', value: '-6'),
        ],
      ),
    ],
    notes: <String>[
      'No century bonus is awarded in T10.',
      'Low strike-rate negatives apply only at 80 or below.',
    ],
  ),
  CricketRuleSet(
    key: 'hundred',
    label: 'The Hundred',
    highlights: <CricketRuleItem>[
      CricketRuleItem(label: 'Wicket', value: '+25 pts'),
      CricketRuleItem(label: 'Run', value: '+1 pts'),
      CricketRuleItem(label: '30 Run Bonus', value: '+5 pts'),
    ],
    categories: <CricketRuleCategory>[
      CricketRuleCategory(
        title: 'Batting',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Run', value: '+1'),
          CricketRuleItem(label: 'Boundary Bonus', value: '+1'),
          CricketRuleItem(label: 'Six Bonus', value: '+2'),
          CricketRuleItem(label: '30 Run Bonus', value: '+5'),
          CricketRuleItem(label: '50 Run Bonus', value: '+10'),
          CricketRuleItem(label: '100 Run Bonus', value: '+20'),
          CricketRuleItem(label: 'Duck', value: '-2'),
        ],
      ),
      CricketRuleCategory(
        title: 'Bowling',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Wicket', value: '+25'),
          CricketRuleItem(label: 'LBW / Bowled Bonus', value: '+8'),
          CricketRuleItem(label: '2 Wicket Bonus', value: '+3'),
          CricketRuleItem(label: '3 Wicket Bonus', value: '+5'),
          CricketRuleItem(label: '4 Wicket Bonus', value: '+10'),
          CricketRuleItem(label: '5 Wicket Bonus', value: '+20'),
        ],
      ),
      CricketRuleCategory(
        title: 'Fielding',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Catch', value: '+8'),
          CricketRuleItem(label: '3 Catch Bonus', value: '+4'),
          CricketRuleItem(label: 'Stumping', value: '+12'),
          CricketRuleItem(label: 'Run Out Direct Hit', value: '+12'),
          CricketRuleItem(label: 'Run Out Assist', value: '+6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Other',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Captain', value: '2x'),
          CricketRuleItem(label: 'Vice Captain', value: '1.5x'),
          CricketRuleItem(label: 'In Announced Lineups', value: '+4'),
          CricketRuleItem(label: 'Playing Substitute', value: '+4'),
        ],
      ),
    ],
    notes: <String>[
      'Official Dream11 Hundred rules page does not list strike-rate or economy modifiers.',
      'Highest batting milestone only.',
    ],
  ),
  CricketRuleSet(
    key: 'other_t20',
    label: 'Other T20',
    highlights: <CricketRuleItem>[
      CricketRuleItem(label: 'Wicket', value: '+30 pts'),
      CricketRuleItem(label: 'Run', value: '+1 pts'),
      CricketRuleItem(label: '30 Run Bonus', value: '+4 pts'),
    ],
    categories: <CricketRuleCategory>[
      CricketRuleCategory(
        title: 'Batting',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Run', value: '+1'),
          CricketRuleItem(label: 'Boundary Bonus', value: '+1'),
          CricketRuleItem(label: 'Six Bonus', value: '+2'),
          CricketRuleItem(label: '30 Run Bonus', value: '+4'),
          CricketRuleItem(label: '50 Run Bonus', value: '+8'),
          CricketRuleItem(label: '100 Run Bonus', value: '+16'),
          CricketRuleItem(label: 'Duck', value: '-2'),
        ],
      ),
      CricketRuleCategory(
        title: 'Bowling',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Wicket', value: '+30'),
          CricketRuleItem(label: 'LBW / Bowled Bonus', value: '+8'),
          CricketRuleItem(label: '3 Wicket Bonus', value: '+4'),
          CricketRuleItem(label: '4 Wicket Bonus', value: '+8'),
          CricketRuleItem(label: '5 Wicket Bonus', value: '+16'),
          CricketRuleItem(label: 'Maiden Over', value: '+12'),
        ],
      ),
      CricketRuleCategory(
        title: 'Fielding',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Catch', value: '+8'),
          CricketRuleItem(label: '3 Catch Bonus', value: '+4'),
          CricketRuleItem(label: 'Stumping', value: '+12'),
          CricketRuleItem(label: 'Run Out Direct Hit', value: '+12'),
          CricketRuleItem(label: 'Run Out Assist', value: '+6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Other',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Captain', value: '2x'),
          CricketRuleItem(label: 'Vice Captain', value: '1.5x'),
        ],
      ),
      CricketRuleCategory(
        title: 'Economy Rate',
        subtitle: 'Min 2 overs',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Below 2.5 runs per over', value: '+6'),
          CricketRuleItem(label: '2.5 - 3.49 runs per over', value: '+4'),
          CricketRuleItem(label: '3.5 - 4.5 runs per over', value: '+2'),
          CricketRuleItem(label: '7 - 8 runs per over', value: '-2'),
          CricketRuleItem(label: '8.02 - 9 runs per over', value: '-4'),
          CricketRuleItem(label: 'Above 9 runs per over', value: '-6'),
        ],
      ),
      CricketRuleCategory(
        title: 'Strike Rate',
        subtitle: 'Except bowlers, min 20 balls',
        items: <CricketRuleItem>[
          CricketRuleItem(label: 'Above 140', value: '+6'),
          CricketRuleItem(label: '120.01 - 140', value: '+4'),
          CricketRuleItem(label: '100 - 120', value: '+2'),
          CricketRuleItem(label: '40 - 50', value: '-2'),
          CricketRuleItem(label: '30 - 39.99', value: '-4'),
          CricketRuleItem(label: 'Below 30', value: '-6'),
        ],
      ),
    ],
    notes: <String>[
      'Boundary and six bonuses are lighter in Other T20.',
      'Low strike-rate negatives apply only at 50 or below.',
    ],
  ),
];

CricketRuleSet resolveCricketRuleSet(String rawFormat) {
  final normalized = rawFormat.trim().toUpperCase().replaceAll(
    RegExp(r'[^A-Z0-9]'),
    '',
  );

  if (normalized.contains('HUNDRED')) {
    return allCricketRuleSets.firstWhere((rule) => rule.key == 'hundred');
  }
  if (normalized.contains('T10')) {
    return allCricketRuleSets.firstWhere((rule) => rule.key == 't10');
  }
  if (normalized.contains('TEST') ||
      normalized.contains('FIRSTCLASS') ||
      normalized == 'FC' ||
      normalized.contains('4DAY') ||
      normalized.contains('5DAY')) {
    return allCricketRuleSets.firstWhere((rule) => rule.key == 'test');
  }
  if (normalized.contains('ODI') ||
      normalized == 'OD' ||
      normalized.contains('ONEDAY') ||
      normalized.contains('LISTA')) {
    return allCricketRuleSets.firstWhere((rule) => rule.key == 'odi');
  }
  if (normalized.contains('T20I') ||
      normalized.contains('IT20') ||
      normalized.contains('TWENTY20INTERNATIONAL')) {
    return allCricketRuleSets.firstWhere((rule) => rule.key == 't20');
  }
  if (normalized.contains('T20')) {
    return allCricketRuleSets.firstWhere((rule) => rule.key == 'other_t20');
  }
  return allCricketRuleSets.firstWhere((rule) => rule.key == 't20');
}
