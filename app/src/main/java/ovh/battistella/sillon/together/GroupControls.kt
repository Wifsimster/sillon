package ovh.battistella.sillon.together

interface GroupControls {
    fun intercept(intent: UserIntent): Boolean
}
