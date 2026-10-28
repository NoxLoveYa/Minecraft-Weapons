# One drop board: 3x5 white, a black aim block at standing eye height (y -59), a green firing spot on the line and a stripe to the board.
$fill $(lx) -60 $(z) $(rx) -56 $(z) white_concrete
$setblock $(x) -59 $(z) black_concrete
$setblock $(x) -61 2 lime_concrete
$fill $(x) -61 3 $(x) -61 $(end) light_gray_concrete
$summon text_display $(x).5 -55.3 $(z).5 {Tags:["pr"],billboard:"center",text:{text:"$(label)",color:"aqua"}}
$summon text_display $(x).5 -60.6 2.5 {Tags:["pr"],billboard:"center",text:{text:"$(label)",color:"green"}}
