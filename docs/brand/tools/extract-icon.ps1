# Extracts the crown mark from the supplied logo tile and emits Android
# adaptive-icon layers:
#   - foreground: crown keyed to transparency, sized inside the 66dp safe circle
#   - monochrome: same silhouette, flat white
# It also reports the diagonal gradient endpoints so the background can be
# rebuilt as a vector (the tile's own gradient is baked in and cannot be reused
# as a background layer without duplicating the crown).
Add-Type -AssemblyName System.Drawing

$src  = $args[0]
$outFg = $args[1]
$outMono = $args[2]

$bmp = [System.Drawing.Bitmap]::FromFile($src)
$W = $bmp.Width; $H = $bmp.Height
$rect = New-Object System.Drawing.Rectangle 0,0,$W,$H
$data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $data.Stride
$buf = New-Object byte[] ($stride * $H)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $buf, 0, $buf.Length)
$bmp.UnlockBits($data)
$bmp.Dispose()

function Px($x,$y){ $i = $y*$stride + $x*4; return @($buf[$i+2], $buf[$i+1], $buf[$i]) }  # R,G,B

# --- 1. locate the coloured tile (page background is near-white / grey) ---
$tx0=$W; $ty0=$H; $tx1=0; $ty1=0
for($y=0;$y -lt $H;$y++){
  for($x=0;$x -lt $W;$x++){
    $p = Px $x $y; $mx=[Math]::Max($p[0],[Math]::Max($p[1],$p[2])); $mn=[Math]::Min($p[0],[Math]::Min($p[1],$p[2]))
    if(($mx-$mn) -gt 40){ if($x -lt $tx0){$tx0=$x}; if($y -lt $ty0){$ty0=$y}; if($x -gt $tx1){$tx1=$x}; if($y -gt $ty1){$ty1=$y} }
  }
}
$tw = $tx1-$tx0+1; $th = $ty1-$ty0+1
Write-Output ("tile: {0},{1} {2}x{3}" -f $tx0,$ty0,$tw,$th)

# --- 2. key the crown: light + desaturated against a saturated gradient ---
# returns alpha 0..255 for a pixel
function CrownAlpha($r,$g,$b){
  $mx=[Math]::Max($r,[Math]::Max($g,$b)); $mn=[Math]::Min($r,[Math]::Min($g,$b))
  if($mx -le 0){ return 0 }
  $sat = ($mx-$mn)/[double]$mx
  $lum = (0.299*$r + 0.587*$g + 0.114*$b)
  if($lum -lt 110){ return 0 }
  # fully crown below sat .22, fades out by sat .42
  if($sat -le 0.22){ $a = 1.0 } elseif($sat -ge 0.42){ $a = 0.0 } else { $a = (0.42-$sat)/0.20 }
  # also fade in with luminance so the darker gradient never leaks
  if($lum -lt 150){ $a = $a * (($lum-110)/40.0) }
  return [int][Math]::Round(255*[Math]::Max(0.0,[Math]::Min(1.0,$a)))
}

# crown bbox + gradient endpoint sampling in one pass.
# INSET: the tile's rounded edge is anti-aliased against the white page, which
# produces light desaturated pixels that key exactly like the crown does. Scan
# well inside the tile so that border can never contaminate either measurement.
$ins = [int][Math]::Round(0.07*[Math]::Max($tw,$th))
$sx0 = $tx0+$ins; $sy0 = $ty0+$ins; $sx1 = $tx1-$ins; $sy1 = $ty1-$ins
Write-Output ("scan inset: {0}px -> {1},{2}..{3},{4}" -f $ins,$sx0,$sy0,$sx1,$sy1)
$cx0=$sx1; $cy0=$sy1; $cx1=$sx0; $cy1=$sy0
$minS=[int]::MaxValue; $maxS=-1; $tlR=0;$tlG=0;$tlB=0; $brR=0;$brG=0;$brB=0
for($y=$sy0;$y -le $sy1;$y++){
  for($x=$sx0;$x -le $sx1;$x++){
    $p = Px $x $y; $a = CrownAlpha $p[0] $p[1] $p[2]
    if($a -gt 24){
      if($x -lt $cx0){$cx0=$x}; if($y -lt $cy0){$cy0=$y}
      if($x -gt $cx1){$cx1=$x}; if($y -gt $cy1){$cy1=$y}
    } else {
      $mx=[Math]::Max($p[0],[Math]::Max($p[1],$p[2])); $mn=[Math]::Min($p[0],[Math]::Min($p[1],$p[2]))
      # a TRUE gradient pixel is strongly saturated; anything washed out is an
      # edge blend and would drag the sampled endpoints toward grey.
      if($mx -gt 0 -and (($mx-$mn)/[double]$mx) -gt 0.42){
        $s = ($x-$tx0)+($y-$ty0)
        if($s -lt $minS){ $minS=$s; $tlR=$p[0];$tlG=$p[1];$tlB=$p[2] }
        if($s -gt $maxS){ $maxS=$s; $brR=$p[0];$brG=$p[1];$brB=$p[2] }
      }
    }
  }
}
$cw = $cx1-$cx0+1; $ch = $cy1-$cy0+1
Write-Output ("crown: {0},{1} {2}x{3}" -f $cx0,$cy0,$cw,$ch)
Write-Output ("gradientStart: #{0:X2}{1:X2}{2:X2}" -f $tlR,$tlG,$tlB)
Write-Output ("gradientEnd:   #{0:X2}{1:X2}{2:X2}" -f $brR,$brG,$brB)

# --- 3. build the keyed crown bitmap ---
$crown = New-Object System.Drawing.Bitmap $cw,$ch,([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cr = New-Object System.Drawing.Rectangle 0,0,$cw,$ch
$cd = $crown.LockBits($cr, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cstride = $cd.Stride
$cbuf = New-Object byte[] ($cstride*$ch)
for($y=0;$y -lt $ch;$y++){
  for($x=0;$x -lt $cw;$x++){
    $p = Px ($cx0+$x) ($cy0+$y); $a = CrownAlpha $p[0] $p[1] $p[2]
    $i = $y*$cstride + $x*4
    # premultiply-safe: store straight colour, alpha in A
    $cbuf[$i]   = [byte]$p[2]
    $cbuf[$i+1] = [byte]$p[1]
    $cbuf[$i+2] = [byte]$p[0]
    $cbuf[$i+3] = [byte]$a
  }
}
[System.Runtime.InteropServices.Marshal]::Copy($cbuf, 0, $cd.Scan0, $cbuf.Length)
$crown.UnlockBits($cd)

# --- 4. scale into a 432x432 (108dp @ xxxhdpi) foreground, inside the safe circle ---
$CANVAS = 432
$SAFE   = 258.0     # 66dp of 108dp -> 264px; held just inside it
$scale  = $SAFE / [Math]::Max($cw,$ch)
$dw = [int][Math]::Round($cw*$scale); $dh = [int][Math]::Round($ch*$scale)
$dx = [int](($CANVAS-$dw)/2); $dy = [int](($CANVAS-$dh)/2)

function Emit($bmpOut,$path){
  $bmpOut.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  Write-Output ("wrote {0}" -f $path)
}

$fg = New-Object System.Drawing.Bitmap $CANVAS,$CANVAS,([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($fg)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)
$g.DrawImage($crown, $dx, $dy, $dw, $dh)
$g.Dispose()
Emit $fg $outFg

# --- 5. monochrome: same silhouette, flat white ---
$mrect = New-Object System.Drawing.Rectangle 0,0,$CANVAS,$CANVAS
$md = $fg.LockBits($mrect, [System.Drawing.Imaging.ImageLockMode]::ReadWrite, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$mstride = $md.Stride
$mbuf = New-Object byte[] ($mstride*$CANVAS)
[System.Runtime.InteropServices.Marshal]::Copy($md.Scan0, $mbuf, 0, $mbuf.Length)
for($i=0;$i -lt $mbuf.Length;$i+=4){
  if($mbuf[$i+3] -gt 0){ $mbuf[$i]=255; $mbuf[$i+1]=255; $mbuf[$i+2]=255 }
}
[System.Runtime.InteropServices.Marshal]::Copy($mbuf, 0, $md.Scan0, $mbuf.Length)
$fg.UnlockBits($md)
Emit $fg $outMono

$fg.Dispose(); $crown.Dispose()
Write-Output "done"
