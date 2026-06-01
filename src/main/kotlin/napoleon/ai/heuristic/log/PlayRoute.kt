package napoleon.ai.heuristic.log

// PlayPlanner 系がフォロー/リードで選んだ「戦術ルート」を表す固定ラベル。ログの reason はこの label
// (固定部分) と呼び出し側が渡す detail (可変の補助情報: 候補札など) を PlayDebugLogger.DETAIL_SEP で
// 連結したもの。stats の PlayBranchScan は reason の固定部分だけで集計するため、ルートを増減しても
// Scan 側の改修は不要。label の "リード:"/"フォロー:" 接頭辞でフェーズを判別する。
//
// 同じ戦術判断はナポ軍・連合で 1 つの値を共有する (ナポ/連合の内訳はログの role= で分かれる)。
// 取る札の向き (最強 vs 最弱) など判断そのものが違うものは別値にする。
enum class PlayRoute(
    val label: String,
) {
    FORCED_SINGLE("合法手1枚で強制"),

    // ---- リード (trickTurn==0) ----
    // ナポレオン第1トリック特化 (NapoleonLeadPlanner.napoleonFirstTrickLead、ナポ専用)
    LEAD_NAP1_MIGHTY_PREEMPT("リード: ナポ第1・マイティで先制"),
    LEAD_NAP1_JOKER_PREEMPT("リード: ナポ第1・ジョーカーで先制"),
    LEAD_NAP1_JOKER_SLIP_AVOID("リード: ナポ第1・SLIP回避でジョーカー先行"),
    LEAD_NAP1_RIGHT_SLIP_AVOID("リード: ナポ第1・SLIP回避で切り札J先行"),
    LEAD_NAP1_MIGHTY_NO_ALT("リード: ナポ第1・代替なくマイティ"),
    LEAD_NAP1_JOKER_NO_ALT("リード: ナポ第1・代替なくジョーカー"),
    LEAD_NAP1_RIGHT_NO_ALT("リード: ナポ第1・代替なく切り札J"),
    LEAD_NAP1_AVOID_TRUMP_PROBE("リード: ナポ第1・切り札回避でサイド低位探り"),
    LEAD_NAP1_TRUMP_ACE_DRAW("リード: ナポ第1・切り札Aで吸い出し"),
    LEAD_NAP1_TRUMP_HIGH_SMASH("リード: ナポ第1・高位切り札ぶち抜き"),
    LEAD_NAP1_TRUMP_KING_DRAW("リード: ナポ第1・切り札Kで吸い出し"),
    LEAD_NAP1_SIDE_ACE_DRAW("リード: ナポ第1・サイドAで吸い出し"),
    LEAD_NAP1_FALLBACK("リード: ナポ第1・フォールバック最強札"),

    // 通常リード
    LEAD_DECISIVE_WIN("リード: 決着確定(自軍勝利)を最強で切る"),
    LEAD_DECISIVE_LOSS("リード: 決着確定(連合勝利)を最強で切る"),
    LEAD_JOKER_CALL("リード: ♣3でジョーカー請求"),
    LEAD_SEIM_TWO("リード: セイム見込みの2を投入"),
    LEAD_CERTAIN_WIN_WEAKEST("リード: 確実勝ち最弱で抜き強札温存"),
    LEAD_TRUMP_DRAW("リード: 高位切り札で敵切り札を吸い出し"),
    LEAD_ENEMY_VOID_RUFF("リード: 敵voidへ低札でラフ誘発"),
    LEAD_ADJUTANT_PROBE("リード: 副官スートを低位で探り"),
    LEAD_LOW_PROBE("リード: 低位非切り札で探り"),
    LEAD_FALLBACK_WEAKEST("リード: 該当なし最小パワー札でしのぐ"),

    // ---- フォロー (trickTurn>0) ----
    FOLLOW_SEIM_REVERSAL("フォロー: セイム成立で2を投入し逆転狙い"),
    FOLLOW_DECISIVE_WIN("フォロー: 決着確定(自軍勝利)を最強で奪取"),
    FOLLOW_DECISIVE_LOSS("フォロー: 決着確定(連合勝利)を保証勝ち最弱で奪取"),
    FOLLOW_CLINCH_HONOR("フォロー: 味方取り切りに絵札上積みで連合勝利確定"),
    FOLLOW_SIDE_HONOR_DUMP("フォロー: 非切り札絵札を吐いて獲得に変える"),
    FOLLOW_TEAM_TAKES_DEAD_HONOR("フォロー: 味方取り切りに死に絵札を上乗せ"),
    FOLLOW_TEAM_LEAD_SECURE_HONOR("フォロー: 味方リード超えられ得るので勝ち札最弱でhonor確保"),
    FOLLOW_TEAM_LEAD_PRESERVE("フォロー: 味方リードにつき最小パワー札で温存"),
    FOLLOW_TEAM_SEIM_RECLAIM("フォロー: セイム崩れ回避で暫定トップを味方に残す最小札で取り返し"),
    FOLLOW_TEAM_DUMP_SAFE("フォロー: 暫定トップが敵に移るので絵札避け献上コスト最小で投げ捨て"),
    FOLLOW_ENEMY_CERTAIN_TAKE("フォロー: 敵リードを勝ち札最弱で取り切り"),
    FOLLOW_ENEMY_BACK_TEAM_DEAD_HONOR("フォロー: 後続全員味方なので敵リードに死に絵札を上乗せ"),
    FOLLOW_ENEMY_CHASE("フォロー: 後続逆転リスク承知で最小コスト追撃"),
    FOLLOW_DUMP_HOLD_CERTAIN("フォロー: 出し捨て(勝ち札温存)"),
    FOLLOW_DUMP_NO_BEATER("フォロー: 出し捨て(超え札なし)"),
    FOLLOW_DUMP_NOT_WORTH("フォロー: 出し捨て(超え可だが追う価値薄)"),
    FOLLOW_DUMP_JOKER_CALL("フォロー: 出し捨て(ジョーカー請求につき絵札温存)"),
}
