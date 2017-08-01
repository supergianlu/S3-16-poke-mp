package controller

import java.util

import com.rabbitmq.client.Connection
import database.remote.DBConnect
import distributed.client.{BattleClientManager, BattleClientManagerImpl}
import distributed.{ConnectedPlayers, ConnectedPlayersObserver}
import model.entities.Trainer
import model.environment.Direction.Direction
import model.environment.{Audio, Coordinate, CoordinateImpl}
import model.map._
import utilities.Settings
import view._

import scala.util.Random

object MapController {
  private final val RANDOM_MAX_VALUE = 10
  private final val MIN_VALUE_TO_FIND_POKEMON = 8
  private final val POKEMON_CENTER_BUILDING = "Pokemon center"
  private final val LABORATORY_BUILDING = "Laboratory"

  def apply(view: View, _trainer: Trainer, connection: Connection, connectedPlayers: ConnectedPlayers): GameController = new MapController(view, _trainer, connection, connectedPlayers)
}

class MapController(private val view: View,
                    private val _trainer: Trainer,
                    private val connection: Connection,
                    private val connectedPlayers: ConnectedPlayers) extends GameControllerImpl(view, _trainer){
  import MapController._

  private val gameMap = MapCreator.create(Settings.Constants.MAP_HEIGHT, Settings.Constants.MAP_WIDTH, InitialTownElements())
  private var lastCoordinates: Coordinate = _
  private val distributedMapController: DistributedMapController = DistributedMapController(this, connection, connectedPlayers)
  connectedPlayers addObserver distributedMapController.asInstanceOf[ConnectedPlayersObserver]
  audio = Audio(Settings.Audio.MAP_SONG)


  override protected def doStart(): Unit = {
    initView()
    if(trainer.capturedPokemons.isEmpty){
      doFirstLogin()
    }else {
      audio.loop()
    }
  }

  private def initView(): Unit = {
    view.showMap(this, distributedMapController, gameMap)
    gamePanel = view.getGamePanel
  }

  private def doFirstLogin(): Unit = {
    pause()
    updateLastCoordinateToBuilding(LABORATORY_BUILDING)
    new LaboratoryController(this.view, this, trainer).start()
  }

  private def updateLastCoordinateToBuilding(building: String): Unit = {
    for( x <- 0 until gameMap.width){
      for( y <- 0 until gameMap.height){
        (building, gameMap.map(x)(y)) match {
          case (LABORATORY_BUILDING, tile:Laboratory) =>
            lastCoordinates = CoordinateImpl(tile.topLeftCoordinate.x + tile.doorCoordinates.x, tile.topLeftCoordinate.y + tile.doorCoordinates.y + 1)
            distributedMapController.sendTrainerPosition(lastCoordinates)
            return
          case (POKEMON_CENTER_BUILDING, tile: PokemonCenter) =>
            lastCoordinates = CoordinateImpl(tile.topLeftCoordinate.x + tile.doorCoordinates.x, tile.topLeftCoordinate.y + tile.doorCoordinates.y + 1)
            distributedMapController.sendTrainerPosition(lastCoordinates)
            return
          case _ =>
        }
      }
    }
  }

  override protected def doPause(): Unit = {
    lastCoordinates = trainer.coordinate
    audio.stop()
    setFocusableOff()
  }

  override protected def doResume(): Unit = {
    distributedMapController sendTrainerInBuilding true
    sendTrainerIsBusy(false)
    if(trainer.getFirstAvailableFavouritePokemon <= 0) {
      DBConnect rechangeAllTrainerPokemon trainer.id
      setTrainerSpriteFront()
      updateLastCoordinateToBuilding(POKEMON_CENTER_BUILDING)
    }
    trainer.coordinate = lastCoordinates
    initView()
    audio.loop()
    setFocusableOn()
  }

  override protected def doTerminate(): Unit = {
    audio.stop()
  }

  override protected def doMove(direction: Direction): Unit = {
    if (!isInPause) {
      if(direction != null) nextPosition = nextTrainerPosition(direction)
      val tile = gameMap.map(nextPosition.x)(nextPosition.y)
      tile match {
        case tile:Building
          if nextPosition equals CoordinateImpl(tile.topLeftCoordinate.x + tile.doorCoordinates.x, tile.topLeftCoordinate.y + tile.doorCoordinates.y) =>
          enterInBuilding(tile)
        case _ if tile.walkable =>
          walk(direction, nextPosition)
          distributedMapController sendTrainerPosition nextPosition
          if(tile.isInstanceOf[Tile.TallGrass]) randomPokemonAppearance()
        case _ => trainerIsMoving = false
      }
    }
  }

  private def enterInBuilding(building: Building) = {
    distributedMapController sendTrainerInBuilding false
    pause()
    val buildingController: BuildingController = building match{
      case _: PokemonCenter => new PokemonCenterController(this.view, this, trainer)
      case _: Laboratory => new LaboratoryController(this.view, this, trainer)
    }
    buildingController.start()
    trainerIsMoving = false
  }

  private def randomPokemonAppearance() = {
    val random: Int = Random nextInt RANDOM_MAX_VALUE
    if(random >= MIN_VALUE_TO_FIND_POKEMON) {
      sendTrainerIsBusy(true)
      waitEndOfMovement.acquire()
      pause()
      waitEndOfMovement.release()
      new BattleControllerImpl(this, view)
    }
  }

  override protected def doInteract(direction: Direction): Unit = {
    if (!isInPause){
      val nextPosition: Coordinate = nextTrainerPosition(direction)
      distributedMapController.connectedPlayers.getAll.values() forEach (otherPlayer =>
        if((nextPosition equals otherPlayer.position) &&  !otherPlayer.isBusy){
          distributedMapController.challengeTrainer(otherPlayer.userId, wantToFight = true, isFirst = true)
          showDialogue(new WaitingTrainerDialoguePanel(otherPlayer.username))
        }else if((nextPosition equals otherPlayer.position) &&  otherPlayer.isBusy){
          showDialogue(new ClassicDialoguePanel(this, util.Arrays.asList(otherPlayer.username + " is busy, try again later!")))
        })
    }
  }

  override protected def doLogout(): Unit = {
    distributedMapController.playerLogout()
    terminate()
  }

  override def createTrainersBattle(otherPlayerId: Int, yourPlayerIsFirst: Boolean): Unit = {
    pause()
    val otherPlayerUsername = (connectedPlayers get otherPlayerId).username
    val distributedBattle: DistributedBattleController = new DistributedBattleControllerImpl(this, view, otherPlayerUsername, yourPlayerIsFirst)
    val battleManager: BattleClientManager = new BattleClientManagerImpl(connection, trainer.id, otherPlayerId, distributedBattle)
    distributedBattle passManager battleManager
  }

  override def sendTrainerIsBusy(isBusy: Boolean): Unit = {
    distributedMapController sendTrainerIsBusy isBusy
  }
}
