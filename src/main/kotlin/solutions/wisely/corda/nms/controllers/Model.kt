package solutions.wisely.corda.nms.controllers


data class SimpleStats (
    val nodeNames: List<String>,
    val notaryNames: List<String>
) {
    val notaryCount: Int
        get() = notaryNames.size
    val nodeCount: Int
        get() = nodeNames.size
}