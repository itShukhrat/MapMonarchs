# Emits a TIGHTLY CROPPED crown for the brand intro.
# The launcher foreground cannot be reused: it is padded to sit inside the 66dp
# adaptive safe circle, so on a full screen the mark would float small inside a
# large transparent box with no way to size it precisely.
Add-Type -AssemblyName System.Drawing

$src = $args[0]
$out = $args[1]

$bmp = [System.Drawing.Bitmap]::FromFile($src)
$W = $bmp.Width; $H = $bmp.Height
$rect = New-Object System.Drawing.Rectangle 0,0,$W,$H
$data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $data.Stride
$buf = New-Object byte[] ($stride * $H)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $buf, 0, $buf.Length)
$bmp.UnlockBits($data); $bmp.Dispose()

function Px($x,$y){ $i = $y*$stride + $x*4; return @($buf[$i+2], $buf[$i+1], $buf[$i]) }

# tile bounds
$tx0=$W; $ty0=$H; $tx1=0; $ty1=0
for($y=0;$y -lt $H;$y++){ for($x=0;$x -lt $W;$x++){
  $p = Px $x $y; $mx=[Math]::Max($p[0],[Math]::Max($p[1],$p[2])); $mn=[Math]::Min($p[0],[Math]::Min($p[1],$p[2]))
  if(($mx-$mn) -gt 40){ if($x -lt $tx0){$tx0=$x}; if($y -lt $ty0){$ty0=$y}; if($x -gt $tx1){$tx1=$x}; if($y -gt $ty1){$ty1=$y} } } }
$tw=$tx1-$tx0+1; $th=$ty1-$ty0+1

function CrownAlpha($r,$g,$b){
  $mx=[Math]::Max($r,[Math]::Max($g,$b)); $mn=[Math]::Min($r,[Math]::Min($g,$b))
  if($mx -le 0){ return 0 }
  $sat = ($mx-$mn)/[double]$mx
  $lum = (0.299*$r + 0.587*$g + 0.114*$b)
  if($lum -lt 110){ return 0 }
  if($sat -le 0.22){ $a = 1.0 } elseif($sat -ge 0.42){ $a = 0.0 } else { $a = (0.42-$sat)/0.20 }
  if($lum -lt 150){ $a = $a * (($lum-110)/40.0) }
  return [int][Math]::Round(255*[Math]::Max(0.0,[Math]::Min(1.0,$a)))
}

# inset past the tile's anti-aliased border, then find the crown's tight bbox
$ins = [int][Math]::Round(0.07*[Math]::Max($tw,$th))
$sx0=$tx0+$ins; $sy0=$ty0+$ins; $sx1=$tx1-$ins; $sy1=$ty1-$ins
$cx0=$sx1; $cy0=$sy1; $cx1=$sx0; $cy1=$sy0
for($y=$sy0;$y -le $sy1;$y++){ for($x=$sx0;$x -le $sx1;$x++){
  $p = Px $x $y
  if((CrownAlpha $p[0] $p[1] $p[2]) -gt 24){
    if($x -lt $cx0){$cx0=$x}; if($y -lt $cy0){$cy0=$y}
    if($x -gt $cx1){$cx1=$x}; if($y -gt $cy1){$cy1=$y} } } }
$cw=$cx1-$cx0+1; $ch=$cy1-$cy0+1
Write-Output ("crown tight bbox: {0},{1} {2}x{3}" -f $cx0,$cy0,$cw,$ch)

$crown = New-Object System.Drawing.Bitmap $cw,$ch,([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cr = New-Object System.Drawing.Rectangle 0,0,$cw,$ch
$cd = $crown.LockBits($cr, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cstride = $cd.Stride
$cbuf = New-Object byte[] ($cstride*$ch)
for($y=0;$y -lt $ch;$y++){ for($x=0;$x -lt $cw;$x++){
  $p = Px ($cx0+$x) ($cy0+$y); $a = CrownAlpha $p[0] $p[1] $p[2]
  $i = $y*$cstride + $x*4
  $cbuf[$i]=[byte]$p[2]; $cbuf[$i+1]=[byte]$p[1]; $cbuf[$i+2]=[byte]$p[0]; $cbuf[$i+3]=[byte]$a } }
[System.Runtime.InteropServices.Marshal]::Copy($cbuf, 0, $cd.Scan0, $cbuf.Length)
$crown.UnlockBits($cd)
$crown.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$crown.Dispose()
Write-Output ("wrote {0} ({1}x{2})" -f $out,$cw,$ch)
