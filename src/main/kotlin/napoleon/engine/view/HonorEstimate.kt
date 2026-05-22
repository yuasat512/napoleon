package napoleon.engine.view

// unknownHonors は所持者の役職がまだ視点プレイヤーに判明していない絵札数。
// 副官が公開されるまでは、ナポレオン側にも連合軍側にもなり得る。
data class HonorEstimate(
    val napoleonHonors: Int,
    val allyHonors: Int,
    val unknownHonors: Int,
)
