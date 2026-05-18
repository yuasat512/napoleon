Set fso = CreateObject("Scripting.FileSystemObject")
Set sh = CreateObject("WScript.Shell")
sh.CurrentDirectory = fso.GetParentFolderName(WScript.ScriptFullName)
If Not fso.FileExists("build\libs\napoleon-all.jar") Then
    MsgBox "napoleon-all.jar not found." & vbCrLf & "Run 'gradlew shadowJar' first.", vbExclamation, "Napoleon"
    WScript.Quit 1
End If
sh.Run "javaw -jar build\libs\napoleon-all.jar", 0, False
